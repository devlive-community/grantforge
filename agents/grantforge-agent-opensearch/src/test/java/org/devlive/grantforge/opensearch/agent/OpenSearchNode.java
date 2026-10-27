// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A single-node OpenSearch cluster of one release in a container, with the security plugin's demo configuration: TLS
 * on HTTP with the demo CA, the {@value #ADMIN} user, the demo admin certificate, whose PEM files are copied out, and the
 * audit log written to the cluster. Requests run as any internal user through curl in the container.
 */
final class OpenSearchNode
        implements AutoCloseable
{
    static final String ADMIN = "admin";
    /** The demo configuration wants a strong password for the admin user. */
    static final String ADMIN_PASSWORD = "GrantForge-Test-2026!";
    /** The password of every other internal user the tests make; the security plugin refuses one like the user's name. */
    static final String PASSWORD = "Tangerine-Orbit-58!";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path files;
    private final GenericContainer<?> container;

    /**
     * The answer to a request.
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
     * @param version the release, such as {@code 3.9.0}
     * @param files a directory for the certificates
     */
    OpenSearchNode(String version, Path files)
    {
        this.files = files;
        container = new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:" + version)).withExposedPorts(9200)
                .withEnv("discovery.type", "single-node").withEnv("OPENSEARCH_INITIAL_ADMIN_PASSWORD", ADMIN_PASSWORD)
                .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m").withEnv("DISABLE_PERFORMANCE_ANALYZER_AGENT_CLI", "true")
                .waitingFor(Wait.forHttps("/_plugins/_security/health").allowInsecure().forPort(9200).forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(5)));
        container.start();
        for (String file : List.of("root-ca.pem", "kirk.pem", "kirk-key.pem")) {
            container.copyFileFromContainer("/usr/share/opensearch/config/" + file, files.resolve(file).toString());
        }
        // The security index is ready a moment after the health check passes.
        Wait.forHttps("/_plugins/_security/authinfo").allowInsecure().forPort(9200).withBasicCredentials(ADMIN, ADMIN_PASSWORD).forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(2)).waitUntilReady(container);
    }

    /** The node by the name its certificate is for. */
    String url()
    {
        return "https://localhost:" + container.getMappedPort(9200);
    }

    /** The demo CA's certificate, PEM. */
    String caCertificate()
    {
        return files.resolve("root-ca.pem").toString();
    }

    /** The demo admin certificate, PEM. */
    String clientCertificate()
    {
        return files.resolve("kirk.pem").toString();
    }

    /** The demo admin certificate's private key, PKCS #8 PEM. */
    String clientKey()
    {
        return files.resolve("kirk-key.pem").toString();
    }

    /**
     * Sends a request as {@value #ADMIN} that must succeed.
     *
     * @param method the HTTP method
     * @param path the path and query
     * @param body the JSON body, or empty for none
     * @return the response body
     */
    String call(String method, String path, String body) throws IOException, InterruptedException
    {
        Result result = admin(method, path, body);
        if (!result.ok()) {
            throw new IllegalStateException(method + " " + path + ": " + result.status() + " " + result.body());
        }
        return result.body();
    }

    /**
     * Sends a request as {@value #ADMIN}.
     *
     * @param method the HTTP method
     * @param path the path and query
     * @param body the JSON body, or empty for none
     * @return the answer
     */
    Result admin(String method, String path, String body) throws IOException, InterruptedException
    {
        return as(ADMIN, ADMIN_PASSWORD, method, path, body);
    }

    /**
     * Sends a request as an internal user the tests made.
     *
     * @param user the user, whose password is {@value #PASSWORD}
     * @param method the HTTP method
     * @param path the path and query
     * @param body the JSON body, or empty for none
     * @return the answer
     */
    Result as(String user, String method, String path, String body) throws IOException, InterruptedException
    {
        return as(user, PASSWORD, method, path, body);
    }

    private Result as(String user, String password, String method, String path, String body) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>(List.of("curl", "-sk", "-u", user + ":" + password, "-X", method,
                "-H", "Content-Type: application/" + (path.contains("_bulk") ? "x-ndjson" : "json"), "-w", "\n%{http_code}",
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
     * Makes an internal user.
     *
     * @param user the name
     * @param backendRoles its backend roles
     */
    void user(String user, String... backendRoles) throws IOException, InterruptedException
    {
        StringBuilder roles = new StringBuilder();
        for (String role : backendRoles) {
            roles.append(roles.length() == 0 ? "" : ",").append('"').append(role).append('"');
        }
        call("PUT", "/_plugins/_security/api/internalusers/" + user, "{\"password\": \"" + PASSWORD + "\", \"backend_roles\": [" + roles + "]}");
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
