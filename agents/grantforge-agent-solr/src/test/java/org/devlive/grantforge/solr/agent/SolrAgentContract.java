// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shaded agent jar in a real SolrCloud node of one Solr release, against a GrantForge server of the tests' own:
 * querying a collection but not through a denied path, updating only through the paths a policy names, administering
 * the collections a policy names through the Collections API, the cluster's administration, policy items that hold for
 * some client addresses only, access events, and a policy change without a restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class SolrAgentContract
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "secret-pass";

    private @Nullable AgentServer server;
    private @Nullable SolrNode node;

    /**
     * The release under test.
     *
     * @return such as {@code 9.11.0}
     */
    abstract String version();

    private static SolrSnapshots policies()
    {
        return new SolrSnapshots()
                .allow(List.of(SolrNode.ADMIN), List.of("admin"), "cluster", "*")
                .allow(List.of("@analysts"), List.of("query"), "collection", "sales")
                .deny(List.of("alice"), List.of("query"), "collection", "sales", "path", "/export")
                .allow(List.of("bob"), List.of("update"), "collection", "sales", "path", "/update*")
                .allow(List.of("carol"), List.of("admin_collection"), "collection", "team-*")
                .allow(List.of("dave"), List.of("metrics"), "cluster", "*")
                .allowFrom(List.of("0.0.0.0/0"), List.of("erin"), List.of("query"), "collection", "hr")
                .allowFrom(List.of("203.0.113.0/24"), List.of("frank"), List.of("query"), "collection", "hr")
                .group("analysts", "alice");
    }

    @BeforeAll
    void start() throws Exception
    {
        AgentServer started = new AgentServer(policies());
        server = started;
        Testcontainers.exposeHostPorts(started.port());
        SolrNode running = new SolrNode(version()).file("/var/solr/data/grantforge.token", AgentServer.TOKEN.getBytes(StandardCharsets.UTF_8))
                .file("/var/solr/data/grantforge.key", started.publicKey().getBytes(StandardCharsets.US_ASCII));
        running.container().withCopyFileToContainer(MountableFile.forHostPath(Path.of(requireNonNull(System.getProperty("grantforge.solr.agent.jar"),
                "grantforge.solr.agent.jar"))), "/opt/solr/server/solr-webapp/webapp/WEB-INF/lib/grantforge-agent-solr.jar");
        node = running;
        ObjectNode authorization = JSON.createObjectNode().put("class", GrantForgeSolrAuthorizer.class.getName())
                .put("grantforge.server.url", "http://host.testcontainers.internal:" + started.port() + "/")
                .put("grantforge.token.file", "/var/solr/data/grantforge.token").put("grantforge.instance", "solr-1")
                .put("grantforge.cache.dir", "/var/solr/data/grantforge-cache").put("grantforge.signing.key.file", "/var/solr/data/grantforge.key")
                .put("grantforge.refresh.interval.ms", "1000").put("grantforge.audit.flush.interval.ms", "200").put("grantforge.audit.fold.window.ms", "0");
        running.start(authorization);
        eventually("the agent takes the policies", () -> status("GET", "/admin/collections?action=LIST", null, SolrNode.ADMIN, SolrNode.ADMIN_PASSWORD) == 200);
        for (String user : List.of("alice", "bob", "carol", "dave", "erin", "frank", "grace", "mallory")) {
            running.user(user, PASSWORD);
        }
        running.collection("sales");
        running.collection("hr");
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private int status(String method, String path, @Nullable String body, @Nullable String user, @Nullable String password)
    {
        try {
            return requireNonNull(node, "node").status(method, path, body, user, password);
        }
        catch (java.io.IOException | InterruptedException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private int as(String user, String method, String path, @Nullable String body)
    {
        return status(method, path, body, user, PASSWORD);
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(500);
        }
    }

    @Test
    void queriesACollectionButNotThroughADeniedPath()
    {
        assertThat(as("alice", "GET", "/sales/select?q=*:*", null)).isEqualTo(200);
        assertThat(as("alice", "GET", "/sales/export?q=*:*&fl=id&sort=id+asc", null)).as("a denial on the path").isEqualTo(403);
        assertThat(as("alice", "GET", "/hr/select?q=*:*", null)).isEqualTo(403);
        assertThat(as("alice", "POST", "/sales/update?commit=true", "[]")).isEqualTo(403);
        assertThat(as("mallory", "GET", "/sales/select?q=*:*", null)).isEqualTo(403);
        assertThat(status("GET", "/sales/select?q=*:*", null, null, null)).isEqualTo(401);
    }

    @Test
    void updatesOnlyThroughThePathsAPolicyNames()
    {
        assertThat(as("bob", "POST", "/sales/update?commit=true", "[{\"id\":\"1\"}]")).isEqualTo(200);
        assertThat(as("bob", "POST", "/sales/update/json?commit=true", "[{\"id\":\"2\"}]")).isEqualTo(200);
        assertThat(as("bob", "GET", "/sales/select?q=*:*", null)).isEqualTo(403);
        assertThat(as("bob", "POST", "/hr/update?commit=true", "[]")).isEqualTo(403);
    }

    @Test
    void administersTheCollectionsAPolicyNames()
    {
        assertThat(as("carol", "GET", "/admin/collections?action=CREATE&numShards=1&collection.configName=_default&name=team-a", null)).isEqualTo(200);
        assertThat(as("carol", "GET", "/admin/collections?action=RELOAD&name=team-a", null)).isEqualTo(200);
        assertThat(as("carol", "GET", "/team-a/select?q=*:*", null)).as("administering a collection includes querying it").isEqualTo(200);
        assertThat(as("carol", "GET", "/admin/collections?action=CREATE&numShards=1&collection.configName=_default&name=other", null)).isEqualTo(403);
        assertThat(as("carol", "GET", "/admin/collections?action=RELOAD&name=sales", null)).isEqualTo(403);
        assertThat(as("carol", "GET", "/admin/collections?action=LIST", null)).isEqualTo(403);
    }

    @Test
    void administersTheClusterUnderPolicy()
    {
        assertThat(as("dave", "GET", "/admin/metrics?group=jvm", null)).isEqualTo(200);
        assertThat(as("dave", "GET", "/admin/collections?action=LIST", null)).isEqualTo(403);
        assertThat(as("dave", "GET", "/admin/authentication", null)).isEqualTo(403);
        assertThat(status("GET", "/admin/authentication", null, SolrNode.ADMIN, SolrNode.ADMIN_PASSWORD)).isEqualTo(200);
    }

    @Test
    void holdsForTheClientAddressesAPolicyNames()
    {
        assertThat(as("erin", "GET", "/hr/select?q=*:*", null)).isEqualTo(200);
        assertThat(as("frank", "GET", "/hr/select?q=*:*", null)).isEqualTo(403);
    }

    @Test
    void appliesAPolicyChangeWithoutARestart() throws Exception
    {
        AgentServer running = requireNonNull(server, "server");
        assertThat(as("grace", "GET", "/hr/select?q=*:*", null)).isEqualTo(403);
        running.publish(policies().allow(List.of("grace"), List.of("query"), "collection", "hr"));
        eventually("grace may query hr", () -> as("grace", "GET", "/hr/select?q=*:*", null) == 200);
        running.publish(policies());
        eventually("grace may no longer", () -> as("grace", "GET", "/hr/select?q=*:*", null) == 403);
    }

    @Test
    void reportsAccessesToTheServer() throws Exception
    {
        assertThat(as("alice", "GET", "/sales/export?q=*:*&fl=id&sort=id+asc", null)).isEqualTo(403);
        AgentServer running = requireNonNull(server, "server");
        eventually("the refusal arrives", () -> running.events().stream().anyMatch(SolrAgentContract::refused));
        JsonNode event = running.events().stream().filter(SolrAgentContract::refused).findFirst().orElseThrow();
        assertThat(event.path("user").asText()).isEqualTo("alice");
        assertThat(event.path("accessType").asText()).isEqualTo("query");
        assertThat(event.path("action").asText()).isEqualTo("read");
        assertThat(event.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(event.path("clientIp").asText()).isNotEmpty();
        assertThat(event.path("resourceType").asText()).isEqualTo("path");
    }

    private static boolean refused(JsonNode event)
    {
        return "collection sales path /export".equals(event.path("resource").asText()) && "DENIED".equals(event.path("outcome").asText());
    }
}
