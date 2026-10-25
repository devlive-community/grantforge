// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * A ClickHouse server of one release in a container, with its HTTP interface and an https one whose certificate a test
 * CA signs; clients that show a certificate of the same CA are verified, so users may sign in with one. The test CA,
 * the server's and a client's certificates are made with the JDK's keytool. Statements run as the administrator.
 */
final class ClickHouseNode
        implements AutoCloseable
{
    static final String ADMIN = "admin";
    static final String ADMIN_PASSWORD = "grantforge-admin";
    /** The user whose certificate {@link #clientCertificate} is. */
    static final String CERTIFICATE_USER = "certuser";

    private static final String STORE_PASSWORD = "changeit";
    /** The PEM label of a PKCS #8 private key, as OpenSSL reads it. */
    private static final String KEY_LABEL = "PRIVATE KEY";
    private static final String TLS = """
            <clickhouse>
                <https_port>8443</https_port>
                <openSSL>
                    <server>
                        <certificateFile>/etc/clickhouse-server/tls/server.crt</certificateFile>
                        <privateKeyFile>/etc/clickhouse-server/tls/server.key</privateKeyFile>
                        <caConfig>/etc/clickhouse-server/tls/ca.crt</caConfig>
                        <verificationMode>relaxed</verificationMode>
                        <loadDefaultCAFile>false</loadDefaultCAFile>
                    </server>
                </openSSL>
            </clickhouse>
            """;

    private final Path files;
    private final GenericContainer<?> container;

    /**
     * Starts the server.
     *
     * @param version the release, such as {@code 26.8.34.13}
     * @param files a directory for the certificates
     */
    ClickHouseNode(String version, Path files) throws IOException, InterruptedException, GeneralSecurityException
    {
        this.files = files;
        certificates();
        container = new GenericContainer<>(DockerImageName.parse("clickhouse/clickhouse-server:" + version)).withExposedPorts(8123, 8443)
                .withEnv("CLICKHOUSE_USER", ADMIN).withEnv("CLICKHOUSE_PASSWORD", ADMIN_PASSWORD)
                .withEnv("CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT", "1")
                .withCopyToContainer(Transferable.of(TLS), "/etc/clickhouse-server/config.d/tls.xml")
                .withCopyToContainer(file("ca.crt"), "/etc/clickhouse-server/tls/ca.crt")
                .withCopyToContainer(file("server.crt"), "/etc/clickhouse-server/tls/server.crt")
                .withCopyToContainer(file("server.key"), "/etc/clickhouse-server/tls/server.key")
                .waitingFor(Wait.forHttp("/ping").forPort(8123).withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
    }

    private Transferable file(String name) throws IOException
    {
        return Transferable.of(Files.readAllBytes(files.resolve(name)), 0644);
    }

    /** The HTTP interface. */
    String url()
    {
        return "http://" + container.getHost() + ":" + container.getMappedPort(8123);
    }

    /** The https interface, by the name its certificate is for. */
    String secureUrl()
    {
        return "https://localhost:" + container.getMappedPort(8443);
    }

    /** The test CA's certificate, PEM. */
    String caCertificate()
    {
        return files.resolve("ca.crt").toString();
    }

    /** The certificate of {@value #CERTIFICATE_USER}, PEM. */
    String clientCertificate()
    {
        return files.resolve("client.crt").toString();
    }

    /** The private key of {@value #CERTIFICATE_USER}'s certificate, PEM. */
    String clientKey()
    {
        return files.resolve("client.key").toString();
    }

    /**
     * Runs statements as the administrator.
     *
     * @param statements the statements, one each
     */
    void run(String... statements) throws IOException, InterruptedException
    {
        for (String statement : statements) {
            var result = container.execInContainer("clickhouse-client", "--user", ADMIN, "--password", ADMIN_PASSWORD, "--query", statement);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(statement + ": " + result.getStderr());
            }
        }
    }

    /**
     * Runs a statement as a user.
     *
     * @param user the user
     * @param password its password
     * @param statement the statement
     * @return what the client wrote, or the error it reported
     */
    Result as(String user, String password, String statement) throws IOException, InterruptedException
    {
        var result = container.execInContainer("clickhouse-client", "--user", user, "--password", password, "--format", "TSVRaw", "--query", statement);
        return new Result(result.getExitCode() == 0, result.getStdout().strip(), result.getStderr().strip());
    }

    /**
     * Reads one value as the administrator.
     *
     * @param query the query
     * @return what it returned, tab-separated
     */
    String value(String query) throws IOException, InterruptedException
    {
        Result result = as(ADMIN, ADMIN_PASSWORD, query);
        if (!result.ok()) {
            throw new IllegalStateException(query + ": " + result.error());
        }
        return result.output();
    }

    /**
     * What a statement run as a user gave.
     *
     * @param ok whether it succeeded
     * @param output what it wrote
     * @param error the error it reported
     */
    record Result(boolean ok, String output, String error)
    {
        /** Whether ClickHouse refused it for a missing privilege. */
        boolean denied()
        {
            return !ok && error.contains("ACCESS_DENIED");
        }
    }

    /** Makes the test CA and the server's and the client's certificates it signs, as PEM files. */
    private void certificates() throws IOException, InterruptedException, GeneralSecurityException
    {
        keytool("-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=GrantForge test CA", "-ext", "bc:c",
                "-validity", "2", "-keystore", store("ca"));
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", store("ca"), "-file", files.resolve("ca.crt").toString());
        signed("server", "CN=localhost", "san=dns:localhost,ip:127.0.0.1", "eku=serverAuth");
        signed("client", "CN=" + CERTIFICATE_USER, null, "eku=clientAuth");
    }

    private void signed(String name, String subject, @Nullable String names, String usage)
            throws IOException, InterruptedException, GeneralSecurityException
    {
        keytool("-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-dname", subject, "-validity", "2", "-keystore", store(name));
        Path request = files.resolve(name + ".csr");
        keytool("-certreq", "-alias", name, "-keystore", store(name), "-file", request.toString());
        List<String> sign = new ArrayList<>(List.of("-gencert", "-rfc", "-alias", "ca", "-keystore", store("ca"), "-infile", request.toString(),
                "-outfile", files.resolve(name + ".crt").toString(), "-validity", "2", "-ext", usage));
        if (names != null) {
            sign.addAll(List.of("-ext", names));
        }
        keytool(sign.toArray(new String[0]));
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(store(name)))) {
            store.load(in, STORE_PASSWORD.toCharArray());
        }
        Key key = requireNonNull(store.getKey(name, STORE_PASSWORD.toCharArray()), name);
        String pem = "-----BEGIN " + KEY_LABEL + "-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(key.getEncoded()) + "\n-----END " + KEY_LABEL + "-----\n";
        Files.writeString(files.resolve(name + ".key"), pem, StandardCharsets.US_ASCII);
    }

    private String store(String name)
    {
        return files.resolve(name + ".p12").toString();
    }

    private void keytool(String... arguments) throws IOException, InterruptedException
    {
        // The command comes first, then its options.
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), arguments[0],
                "-storetype", "PKCS12", "-storepass", STORE_PASSWORD));
        command.addAll(List.of(arguments).subList(1, arguments.length));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(1, TimeUnit.MINUTES) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool " + String.join(" ", arguments) + ": " + output);
        }
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
