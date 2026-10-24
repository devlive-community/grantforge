// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * A SolrCloud node of one Solr release in a container, with its embedded ZooKeeper, basic authentication and the
 * security settings the tests give it, uploaded to ZooKeeper as an operator does. Its administrator is {@code solr}
 * with the password {@code SolrRocks}, the example Solr's own documentation gives.
 */
final class SolrCluster
        implements AutoCloseable
{
    static final String ADMIN = "solr";
    static final String ADMIN_PASSWORD = "SolrRocks";
    static final int PORT = 8983;

    /** The salted SHA-256 of {@link #ADMIN_PASSWORD} with its salt, from Solr's documentation. */
    private static final String ADMIN_CREDENTIALS = "IV0EHq1OnNrj6gvRCwvFwTrZ1+z1oBbnQdiVC3otuq0= Ndd7LKvVBAaZIF0QAVi1ekCfAJXr1GGfLtRUXhgrF8c=";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final GenericContainer<?> container;
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * A node of a release.
     *
     * @param version the release, such as {@code 9.11.0}
     */
    @SuppressWarnings("resource")
    SolrCluster(String version)
    {
        // Solr 10 runs SolrCloud by default and no longer takes -c.
        String[] command = version.startsWith("9.") ? new String[] {"solr-foreground", "-c"} : new String[] {"solr-foreground"};
        container = new GenericContainer<>(DockerImageName.parse("solr:" + version)).withExposedPorts(PORT).withEnv("SOLR_HEAP", "512m")
                .withCommand(command).waitingFor(Wait.forHttp("/solr/admin/info/system").forPort(PORT).withStartupTimeout(Duration.ofMinutes(3)));
    }

    GenericContainer<?> container()
    {
        return container;
    }

    /**
     * The security settings Solr's rule-based authorization takes: the administrator may do everything, other users
     * nothing.
     *
     * @return the authorization section
     */
    static ObjectNode ruleBasedAuthorization()
    {
        ObjectNode authorization = JSON.createObjectNode().put("class", "solr.RuleBasedAuthorizationPlugin");
        authorization.putArray("permissions").addObject().put("name", "all").put("role", "admin");
        authorization.putObject("user-role").put(ADMIN, "admin");
        return authorization;
    }

    /**
     * Starts the node and secures it with basic authentication and an authorization section.
     *
     * @param authorization the authorization section of security.json
     * @throws Exception when the node does not start or take the settings
     */
    void start(ObjectNode authorization) throws Exception
    {
        ObjectNode security = JSON.createObjectNode();
        ObjectNode authentication = security.putObject("authentication").put("blockUnknown", true).put("class", "solr.BasicAuthPlugin");
        authentication.putObject("credentials").put(ADMIN, ADMIN_CREDENTIALS);
        security.set("authorization", authorization);
        container.withCopyToContainer(Transferable.of(JSON.writeValueAsBytes(security)), "/tmp/security.json");
        container.start();
        Container.ExecResult copied = container.execInContainer("bin/solr", "zk", "cp", "file:/tmp/security.json", "zk:/security.json", "-z", "localhost:9983");
        if (copied.getExitCode() != 0) {
            throw new IllegalStateException("security.json was not uploaded: " + copied.getStderr());
        }
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (status("GET", "/admin/info/system", null, null, null) != 401) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Solr did not take its security settings");
            }
            Thread.sleep(500);
        }
    }

    /** The node's base URL, as a service names it. */
    String url()
    {
        return "http://" + container.getHost() + ":" + container.getMappedPort(PORT) + "/solr";
    }

    /**
     * Adds a user, as the administrator.
     *
     * @param user the name
     * @param password the password
     * @throws IOException when Solr cannot be asked
     * @throws InterruptedException when interrupted
     */
    void user(String user, String password) throws IOException, InterruptedException
    {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("set-user").put(user, password);
        expect(200, "POST", "/admin/authentication", JSON.writeValueAsString(body), ADMIN, ADMIN_PASSWORD);
    }

    /**
     * Creates a collection of one shard with the default configuration, as the administrator.
     *
     * @param name the collection
     * @throws IOException when Solr cannot be asked
     * @throws InterruptedException when interrupted
     */
    void collection(String name) throws IOException, InterruptedException
    {
        expect(200, "GET", "/admin/collections?action=CREATE&numShards=1&collection.configName=_default&name=" + name, null, ADMIN, ADMIN_PASSWORD);
    }

    private void expect(int status, String method, String path, @Nullable String body, String user, String password) throws IOException, InterruptedException
    {
        int answered = status(method, path, body, user, password);
        if (answered != status) {
            throw new IllegalStateException(method + " " + path + " answered " + answered);
        }
    }

    /**
     * Sends a request and returns Solr's status.
     *
     * @param method GET or POST
     * @param path below the base URL, with its query
     * @param body a JSON body, or {@code null}
     * @param user who signs in, or {@code null} for no one
     * @param password the password
     * @return the HTTP status
     * @throws IOException when Solr cannot be asked
     * @throws InterruptedException when interrupted
     */
    int status(String method, String path, @Nullable String body, @Nullable String user, @Nullable String password) throws IOException, InterruptedException
    {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url() + path)).timeout(Duration.ofSeconds(60))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Content-Type", "application/json");
        if (user != null) {
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Override
    public void close()
    {
        container.stop();
    }
}
