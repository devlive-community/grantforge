// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

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
import java.nio.file.attribute.PosixFilePermissions;
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
 * A PostgreSQL server of one release in a container, with TLS whose certificate a test CA signs; a client that shows a
 * certificate of the same CA signs in as {@value #CERTIFICATE_USER} by PostgreSQL's {@code cert} authentication, others
 * with a password. The test CA, the server's and a client's certificates are made with the JDK's keytool. Statements run
 * as the superuser.
 */
final class PostgreSqlNode
        implements AutoCloseable
{
    static final String ADMIN = "postgres";
    static final String ADMIN_PASSWORD = "grantforge-admin";
    /** The user whose certificate {@link #clientCertificate} is. */
    static final String CERTIFICATE_USER = "certuser";

    private static final String STORE_PASSWORD = "changeit";
    /** The PEM label of a PKCS #8 private key, as OpenSSL reads it. */
    private static final String KEY_LABEL = "PRIVATE KEY";
    private static final String HBA = """
            local all all trust
            hostssl all certuser all cert
            host all all all scram-sha-256
            """;
    /** Puts the certificates where only the server's account reads them, as PostgreSQL wants its key, and starts it. */
    private static final String START = "install -d -o postgres -g postgres -m 700 /tls && install -o postgres -g postgres -m 600 /tls-in/* /tls/"
            + " && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tls/server.crt -c ssl_key_file=/tls/server.key"
            + " -c ssl_ca_file=/tls/ca.crt -c hba_file=/tls/pg_hba.conf";

    private final Path files;
    private final GenericContainer<?> container;

    /**
     * Starts the server.
     *
     * @param version the release, such as {@code 17.11}
     * @param files a directory for the certificates
     */
    PostgreSqlNode(String version, Path files) throws IOException, InterruptedException, GeneralSecurityException
    {
        this.files = files;
        certificates();
        container = new GenericContainer<>(DockerImageName.parse("postgres:" + version)).withExposedPorts(5432)
                .withEnv("POSTGRES_PASSWORD", ADMIN_PASSWORD)
                .withCopyToContainer(Transferable.of(HBA), "/tls-in/pg_hba.conf")
                .withCopyToContainer(file("ca.crt"), "/tls-in/ca.crt")
                .withCopyToContainer(file("server.crt"), "/tls-in/server.crt")
                .withCopyToContainer(file("server.key"), "/tls-in/server.key")
                .withCreateContainerCmdModifier(command -> command.withEntrypoint("bash", "-c", START).withCmd(List.of()))
                // The server the image starts to initialize the database listens on no TCP address.
                .waitingFor(Wait.forSuccessfulCommand("pg_isready -h 127.0.0.1 -U postgres").withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
    }

    private Transferable file(String name) throws IOException
    {
        return Transferable.of(Files.readAllBytes(files.resolve(name)), 0644);
    }

    /** The server, as {@code host:port}. */
    String host()
    {
        return container.getHost() + ":" + container.getMappedPort(5432);
    }

    /** The server by the name its certificate is for. */
    String secureHost()
    {
        return "localhost:" + container.getMappedPort(5432);
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

    /** The private key of {@value #CERTIFICATE_USER}'s certificate, PKCS #8 PEM. */
    String clientKey()
    {
        return files.resolve("client.key").toString();
    }

    /**
     * Runs statements in a database as the superuser, each on its own.
     *
     * @param database the database
     * @param statements the statements
     */
    void run(String database, String... statements) throws IOException, InterruptedException
    {
        for (String statement : statements) {
            var result = container.execInContainer("psql", "-U", ADMIN, "-d", database, "-v", "ON_ERROR_STOP=1", "-c", statement);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(statement + ": " + result.getStderr());
            }
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
        Path written = Files.writeString(files.resolve(name + ".key"), pem, StandardCharsets.US_ASCII);
        // The driver, like libpq, refuses a private key that others may read.
        Files.setPosixFilePermissions(written, PosixFilePermissions.fromString("rw-------"));
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
