// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A single-node Elasticsearch cluster of one release in a container, with security on: TLS on HTTP with the test
 * certificates, the {@value #ADMIN} superuser, a PKI realm that signs in the test client certificate as
 * {@value #CERTIFICATE_USER}, and the audit log of granted and denied accesses written to a file of a directory the
 * test sees. The cluster starts with a trial license, which has every feature. Requests run through curl in the
 * container.
 */
final class ElasticsearchNode
        implements AutoCloseable
{
    static final String ADMIN = "elastic";
    static final String ADMIN_PASSWORD = "GrantForge-Test-2026!";
    /** The password of every other user. */
    static final String PASSWORD = "Tangerine-Orbit-58!";
    /** The user the PKI realm takes from the test client certificate's common name. */
    static final String CERTIFICATE_USER = "client";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CERTS = "/usr/share/elasticsearch/config/certs/";

    private final TestCertificates certificates;
    private final Path logs;
    private final GenericContainer<?> container;

    /**
     * The answer of a request.
     *
     * @param status the HTTP status
     * @param body the body
     */
    record Result(int status, String body)
    {
        boolean ok()
        {
            return status >= 200 && status < 300;
        }

        boolean denied()
        {
            return status == 403;
        }

        JsonNode json() throws IOException
        {
            return JSON.readTree(body);
        }
    }

    /**
     * Starts the cluster.
     *
     * @param version the release, such as {@code 9.5.5}
     * @param certificates the test certificates, whose server certificate the node serves
     * @param logs a directory the node writes its logs to, the audit log among them
     */
    ElasticsearchNode(String version, TestCertificates certificates, Path logs) throws IOException
    {
        this.certificates = certificates;
        this.logs = logs;
        // The node runs as a user of its own, which writes to the directory.
        Files.setPosixFilePermissions(logs, PosixFilePermissions.fromString("rwxrwxrwx"));
        GenericContainer<?> node = new GenericContainer<>(DockerImageName.parse("elasticsearch:" + version)).withExposedPorts(9200)
                .withEnv("discovery.type", "single-node").withEnv("ELASTIC_PASSWORD", ADMIN_PASSWORD).withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withEnv("ES_LOG_STYLE", "file").withEnv("xpack.security.enabled", "true").withEnv("xpack.license.self_generated.type", "trial")
                .withEnv("xpack.ml.enabled", "false").withEnv("xpack.security.http.ssl.enabled", "true")
                .withEnv("xpack.security.http.ssl.certificate", CERTS + "server.pem").withEnv("xpack.security.http.ssl.key", CERTS + "server-key.pem")
                .withEnv("xpack.security.http.ssl.certificate_authorities", CERTS + "ca.pem").withEnv("xpack.security.http.ssl.client_authentication", "optional")
                .withEnv("xpack.security.authc.realms.pki.pki1.order", "1").withEnv("xpack.security.audit.enabled", "true")
                .withEnv("xpack.security.audit.logfile.events.include", "access_granted,access_denied")
                .withFileSystemBind(logs.toString(), "/usr/share/elasticsearch/logs", BindMode.READ_WRITE)
                .waitingFor(Wait.forHttps("/_security/_authenticate").allowInsecure().forPort(9200).withBasicCredentials(ADMIN, ADMIN_PASSWORD)
                        .forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));
        for (String file : List.of("ca.pem", "server.pem", "server-key.pem")) {
            node.withCopyFileToContainer(MountableFile.forHostPath(certificates.file(file), 0644), CERTS + file);
        }
        container = node;
        container.start();
    }

    /** The node by the name its certificate is for. */
    String url()
    {
        return "https://localhost:" + container.getMappedPort(9200);
    }

    /** The test CA's certificate, PEM. */
    String caCertificate()
    {
        return certificates.file("ca.pem");
    }

    /** The test client certificate, PEM. */
    String clientCertificate()
    {
        return certificates.file("client.pem");
    }

    /** The test client certificate's private key, PKCS #8 PEM. */
    String clientKey()
    {
        return certificates.file("client-pkcs8.pem");
    }

    /** The audit log file the node writes, which the test sees. */
    Path auditLog()
    {
        return logs.resolve("docker-cluster_audit.json");
    }

    /**
     * Sends a request as {@value #ADMIN}, which must succeed.
     *
     * @param method the HTTP method
     * @param path the path and query
     * @param body the JSON body, or empty for none
     * @return the answer's body
     */
    String call(String method, String path, String body) throws IOException, InterruptedException
    {
        Result result = admin(method, path, body);
        if (!result.ok()) {
            throw new IllegalStateException(method + " " + path + ": " + result.status() + " " + result.body());
        }
        return result.body();
    }

    /** Sends a request as {@value #ADMIN}. */
    Result admin(String method, String path, String body) throws IOException, InterruptedException
    {
        return as(ADMIN, ADMIN_PASSWORD, method, path, body);
    }

    /** Sends a request as a user with the test password. */
    Result as(String user, String method, String path, String body) throws IOException, InterruptedException
    {
        return as(user, PASSWORD, method, path, body);
    }

    private Result as(String user, String password, String method, String path, String body) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>(List.of("curl", "-sk", "-u", user + ":" + password, "-X", method,
                "-H", "Content-Type: application/" + (path.contains("_bulk") || path.contains("_msearch") ? "x-ndjson" : "json"), "-w", "\n%{http_code}",
                "https://localhost:9200" + path));
        if (!body.isEmpty()) {
            command.addAll(List.of("--data-binary", body));
        }
        var result = container.execInContainer(command.toArray(new String[0]));
        String output = result.getStdout();
        int end = output.lastIndexOf('\n');
        if (result.getExitCode() != 0 || end < 0) {
            throw new IllegalStateException(method + " " + path + ": " + output + result.getStderr());
        }
        return new Result(Integer.parseInt(output.substring(end + 1).strip()), output.substring(0, end));
    }

    /**
     * Creates a native user with the test password.
     *
     * @param user the name
     * @param roles the roles it holds
     */
    void user(String user, String... roles) throws IOException, InterruptedException
    {
        StringBuilder list = new StringBuilder();
        for (String role : roles) {
            list.append(list.length() == 0 ? "" : ",").append('"').append(role).append('"');
        }
        call("PUT", "/_security/user/" + user, "{\"password\": \"" + PASSWORD + "\", \"roles\": [" + list + "]}");
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
