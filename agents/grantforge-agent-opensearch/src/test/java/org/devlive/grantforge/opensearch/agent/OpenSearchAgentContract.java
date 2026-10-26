// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent against a real OpenSearch cluster of one release with the security plugin: reading by index and by field
 * through field level security, backend roles as groups, index patterns granted as patterns so that indices created
 * later are reachable at once, and by name where a policy decides otherwise within them, masked fields, document level
 * security and row filters OpenSearch would not run, the cluster's own operations, changes of policies and of the
 * service, changes made by hand undone, the users it leaves alone, the audit log as access events with roles outside
 * GrantForge told apart and reported, sign-in with a client certificate, and the shaded jar run as a service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class OpenSearchAgentContract
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> USERS = List.of("alice", "bob", "dave", "erin", "etl", "writer", "frank");

    /** The certificates and the agents' files; the cluster starts before the instance's own directories exist. */
    @TempDir
    static Path files;

    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            logged.add(record);
        }

        @Override
        public void flush()
        {
            // Nothing is buffered.
        }

        @Override
        public void close()
        {
            // Nothing to let go of.
        }
    };
    private final Logger logger = Logger.getLogger("org.devlive.grantforge.opensearch.agent");
    private @Nullable OpenSearchNode node;
    private @Nullable AgentServer server;
    private @Nullable OpenSearchAgent agent;
    private long published = 1;

    /**
     * The release under test.
     *
     * @return such as {@code 3.9.0}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        OpenSearchNode started = new OpenSearchNode(version(), Files.createDirectories(files.resolve("tls")));
        node = started;
        for (String user : USERS) {
            started.user(user);
        }
        started.user("carol", "analysts");
        started.user("root");
        started.call("PUT", "/_plugins/_security/api/rolesmapping/readall", "{\"users\": [\"dave\"]}");
        started.call("PUT", "/_plugins/_security/api/rolesmapping/all_access", "{\"backend_roles\": [\"admin\"], \"users\": [\"root\"]}");
        started.call("PATCH", "/_plugins/_security/api/audit", "[{\"op\": \"replace\", \"path\": \"/config/audit/disabled_rest_categories\","
                + " \"value\": [\"AUTHENTICATED\"]}, {\"op\": \"replace\", \"path\": \"/config/audit/disabled_transport_categories\","
                + " \"value\": [\"AUTHENTICATED\"]}]");
        started.call("PUT", "/logs-eu", "{\"mappings\": {\"properties\": {\"region\": {\"type\": \"keyword\"}, \"card\": {\"type\": \"keyword\"},"
                + " \"name\": {\"type\": \"keyword\"}}}}");
        started.call("PUT", "/logs-eu/_doc/1?refresh=true", "{\"region\": \"EU\", \"card\": \"4111111111111111\", \"name\": \"Anna\"}");
        started.call("PUT", "/logs-eu/_doc/2?refresh=true", "{\"region\": \"APAC\", \"card\": \"340000000000009\", \"name\": \"Chen\"}");
        started.call("PUT", "/logs-us/_doc/1?refresh=true", "{\"region\": \"US\", \"card\": \"5500000000000004\", \"name\": \"Bob\"}");
        started.call("PUT", "/metrics/_doc/1?refresh=true", "{\"cpu\": 0.5}");
        started.call("PUT", "/hr/_doc/1?refresh=true", "{\"employee\": \"ann\", \"salary\": 1000}");
        started.call("PUT", "/team-a/_doc/1?refresh=true", "{\"task\": \"plan\"}");
        logger.addHandler(capture);
        server = new AgentServer(new OpenSearchSnapshots());
        agent = OpenSearchAgent.start(AgentConfig.parse(settings("main", "grantforge_", "opensearch.user", OpenSearchNode.ADMIN,
                "opensearch.password.file", file("agent.password", OpenSearchNode.ADMIN_PASSWORD)), "test"));
        awaitPolicies();
    }

    @AfterAll
    void stop()
    {
        logger.removeHandler(capture);
        if (agent != null) {
            agent.close();
        }
        if (server != null) {
            server.close();
        }
        if (node != null) {
            node.close();
        }
    }

    private OpenSearchNode node()
    {
        return requireNonNull(node, "node");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private OpenSearchAgent agent()
    {
        return requireNonNull(agent, "agent");
    }

    private static String file(String name, String content) throws IOException
    {
        Path path = files.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toString();
    }

    /** The settings of an agent of the test server, with timers too slow to run on their own but the first sync. */
    private Map<String, String> settings(String instance, String prefix, String... more) throws IOException
    {
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + server().port(),
                "grantforge.token.file", file(instance + ".token", AgentServer.TOKEN), "grantforge.instance", "opensearch-" + instance,
                "grantforge.cache.dir", files.resolve("cache-" + instance).toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.opensearch.hosts", node().url(), "grantforge.opensearch.role.prefix", prefix));
        values.putAll(Map.of("grantforge.opensearch.unmanaged.users", "etl", "grantforge.sync.interval.ms", "3600000",
                "grantforge.audit.log.interval.ms", "600000", "grantforge.audit.fold.window.ms", "0", "grantforge.opensearch.tls.ca",
                node().caCertificate()));
        for (int index = 0; index < more.length; index += 2) {
            values.put("grantforge." + more[index], more[index + 1]);
        }
        return values;
    }

    /** Publishes policies, waits until the agent has them and syncs. */
    private Reconciler.Outcome publish(OpenSearchSnapshots policies) throws InterruptedException
    {
        server().publish(policies);
        published++;
        awaitPolicies();
        return requireNonNull(agent().syncNow(), "outcome");
    }

    private void awaitPolicies() throws InterruptedException
    {
        eventually("the agent takes policy version " + published, () -> {
            Snapshot snapshot = agent().agent().snapshot();
            return snapshot != null && snapshot.policyVersion() == published;
        });
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException
    {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(200);
        }
    }

    private static String[] fields(String index, String field)
    {
        return new String[] {"index", index, "field", field};
    }

    /** The documents a user's search of an index returns, as their sources. */
    private List<JsonNode> search(String user, String index) throws IOException, InterruptedException
    {
        OpenSearchNode.Result result = node().as(user, "GET", "/" + index + "/_search?sort=_id", "");
        assertThat(result.ok()).as(user + " searches " + index + ": " + result.body()).isTrue();
        List<JsonNode> sources = new ArrayList<>();
        result.json().path("hits").path("hits").forEach(hit -> sources.add(hit.path("_source")));
        return sources;
    }

    private boolean denied(String user, String method, String path, String body) throws IOException, InterruptedException
    {
        return node().as(user, method, path, body).denied();
    }

    private JsonNode role(String name) throws IOException, InterruptedException
    {
        return JSON.readTree(node().call("GET", "/_plugins/_security/api/roles/" + name, "")).path(name);
    }

    private boolean exists(String role) throws IOException, InterruptedException
    {
        return node().admin("GET", "/_plugins/_security/api/roles/" + role, "").ok();
    }

    private boolean warned(String text)
    {
        return logged.stream().anyMatch(record -> String.valueOf(record.getMessage()).contains(text));
    }

    @Test
    void grantsReadingByIndexAndByFieldAndThroughBackendRoles() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-*", "*"))
                .allow(List.of("bob"), List.of("read"), fields("hr", "*")).deny(List.of("bob"), List.of("read"), fields("hr", "salary"))
                .allow(List.of("@analysts"), List.of("read"), fields("metrics", "*")));

        assertThat(search("alice", "logs-eu")).hasSize(2);
        assertThat(search("alice", "logs-*")).hasSize(3);
        assertThat(denied("alice", "GET", "/metrics/_search", "")).isTrue();
        assertThat(role("grantforge_alice").path("index_permissions").path(0).path("index_patterns").toString())
                .as("the pattern stays a pattern").isEqualTo("[\"logs-*\"]");
        assertThat(role("grantforge_alice").path("cluster_permissions").toString()).contains("cluster_composite_ops_ro");

        List<JsonNode> bob = search("bob", "hr");
        assertThat(bob).hasSize(1).allMatch(source -> !source.has("salary") && source.has("employee"));
        assertThat(role("grantforge_bob").path("index_permissions").path(0).path("fls").toString()).isEqualTo("[\"employee\"]");

        assertThat(search("carol", "metrics")).as("through the backend role analysts").hasSize(1);
        assertThat(denied("carol", "GET", "/logs-eu/_search", "")).isTrue();
    }

    @Test
    void patternsReachIndicesCreatedLaterAndNamesWhereAPolicyDecidesOtherwise() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("writer"), List.of("index", "create_index"), "index", "app-logs-*")
                .allow(List.of("writer"), List.of("read"), fields("app-logs-*", "*"))
                .allow(List.of("frank"), List.of("index", "create_index"), "index", "team-*")
                .deny(List.of("frank"), List.of("index"), "index", "team-secret"));

        OpenSearchNode.Result created = node().as("writer", "POST", "/app-logs-2027/_doc?refresh=true", "{\"message\": \"tomorrow\"}");
        assertThat(created.status()).as("an index created after the sync: " + created.body()).isEqualTo(201);
        assertThat(search("writer", "app-logs-2027")).hasSize(1);

        assertThat(node().as("frank", "POST", "/team-a/_doc", "{\"task\": \"build\"}").status()).isEqualTo(201);
        assertThat(role("grantforge_frank").path("index_permissions").toString()).as("team-* holds team-secret, which a policy decides otherwise")
                .contains("\"team-a\"").doesNotContain("team-*");
        node().call("PUT", "/team-b/_doc/1?refresh=true", "{\"task\": \"test\"}");
        try {
            assertThat(denied("frank", "POST", "/team-b/_doc", "{\"task\": \"x\"}")).as("an index granted by name waits for the next sync").isTrue();
            agent().syncNow();
            assertThat(node().as("frank", "POST", "/team-b/_doc", "{\"task\": \"x\"}").status()).isEqualTo(201);
        }
        finally {
            node().call("DELETE", "/team-b", "");
        }
        assertThat(denied("frank", "POST", "/team-secret/_doc", "{\"task\": \"x\"}")).isTrue();
    }

    @Test
    void masksFieldsAndFiltersDocuments() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("erin"), List.of("read"), fields("logs-eu", "*"))
                .allow(List.of("erin"), List.of("read"), fields("logs-us", "*"))
                .mask(List.of("erin"), "mask_show_last_4", null, "logs-eu", "card").mask(List.of("erin"), "custom", "SHA-512", "logs-eu", "name")
                .filter(List.of("erin"), "{\"term\": {\"region\": \"EU\"}}", "logs-eu").filter(List.of("erin"), "{\"bogus\": {}}", "logs-us"));

        List<JsonNode> eu = search("erin", "logs-eu");
        assertThat(eu).hasSize(1);
        assertThat(eu.get(0).path("card").asText()).isEqualTo("xxxxxxxxxxxx1111");
        assertThat(eu.get(0).path("name").asText()).hasSize(128).isNotEqualTo("Anna");
        assertThat(search("erin", "logs-us")).as("OpenSearch would not run the query, so no document shows").isEmpty();
        assertThat(warned("with a query OpenSearch would not run")).isTrue();
    }

    @Test
    void grantsTheClustersOwnOperations() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("erin"), List.of("cluster_monitor"), "cluster", "*")
                .allow(List.of("alice"), List.of("read"), fields("logs-us", "*")));
        assertThat(node().as("erin", "GET", "/_cluster/health", "").ok()).isTrue();
        assertThat(denied("alice", "GET", "/_cluster/health", "")).isTrue();
        OpenSearchNode.Result searched = node().as("alice", "POST", "/_msearch", "{\"index\": \"logs-us\"}\n{\"query\": {\"match_all\": {}}}\n");
        assertThat(searched.ok()).as("reading comes with multi-search: " + searched.body()).isTrue();
    }

    @Test
    void policyAndServiceChangesTakeEffectAndRolesGo() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")));
        assertThat(search("alice", "logs-us")).hasSize(1);
        Reconciler.Outcome off = publish(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")).disabled());
        assertThat(off.dropped()).isPositive();
        assertThat(exists("grantforge_alice")).isFalse();
        assertThat(denied("alice", "GET", "/logs-us/_search", "")).isTrue();
        assertThat(warned("is not in use")).isTrue();
        publish(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")).serviceType("hive"));
        assertThat(warned("not an OpenSearch one")).isTrue();
        assertThat(exists("grantforge_alice")).isFalse();
    }

    @Test
    void undoesChangesMadeByHand() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")));
        node().call("PUT", "/_plugins/_security/api/roles/grantforge_alice", "{\"description\": \"GrantForge agent role of user alice\","
                + " \"index_permissions\": [{\"index_patterns\": [\"hr\"], \"allowed_actions\": [\"read\"]}]}");
        node().call("PUT", "/_plugins/_security/api/rolesmapping/grantforge_alice", "{\"users\": [\"alice\", \"bob\"]}");
        assertThat(search("bob", "hr")).hasSize(1);
        Reconciler.Outcome restored = requireNonNull(agent().syncNow(), "outcome");
        assertThat(restored.updated()).isEqualTo(1);
        assertThat(restored.mappings()).isPositive();
        assertThat(denied("alice", "GET", "/hr/_search", "")).isTrue();
        assertThat(denied("bob", "GET", "/hr/_search", "")).isTrue();
        assertThat(search("alice", "logs-us")).hasSize(1);
    }

    @Test
    void leavesItselfSuperusersAndUnmanagedUsersAlone() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("etl", "root", OpenSearchNode.ADMIN), List.of("read"), fields("*", "*")));
        assertThat(exists("grantforge_etl")).isFalse();
        assertThat(exists("grantforge_root")).isFalse();
        assertThat(exists("grantforge_admin")).isFalse();
        assertThat(warned("OpenSearch user root is mapped to all_access")).isTrue();
    }

    @Test
    void reportsAccessesFromTheAuditLogAndRolesOutsideGrantForge() throws Exception
    {
        OpenSearchSnapshots policies = new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-*", "*"));
        long denial = policies.nextId();
        policies.deny(List.of("bob"), List.of("read"), fields("logs-us", "*"));
        long outside = policies.nextId();
        policies.deny(List.of("dave"), List.of("read"), fields("hr", "*"));
        publish(policies);
        assertThat(warned("OpenSearch role readall, which user dave has outside GrantForge, gives read on hr, which GrantForge policy " + outside
                + " denies")).isTrue();
        agent().readLogNow();

        search("alice", "logs-eu");
        assertThat(denied("bob", "GET", "/logs-us/_search", "")).isTrue();
        search("dave", "hr");

        JsonNode alice = event("alice", "read", "logs-eu");
        assertThat(alice.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(alice.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(alice.path("action").asText()).startsWith("indices:data/read/search");
        JsonNode bob = event("bob", "read", "logs-us");
        assertThat(bob.path("outcome").asText()).isEqualTo("DENIED");
        assertThat(bob.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(bob.path("policyId").asText()).isEqualTo(Long.toString(denial));
        JsonNode dave = event("dave", "read", "hr");
        assertThat(dave.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(dave.path("enforcer").asText()).as("a role outside GrantForge allowed it").isEqualTo("NATIVE");
        assertThat(server().events()).noneMatch(event -> OpenSearchNode.ADMIN.equals(event.path("user").asText()));
    }

    private JsonNode event(String user, String type, String resource) throws InterruptedException
    {
        eventually("an event of " + user + " on " + resource + " arrives", () -> {
            agent().readLogNow();
            return server().events().stream().anyMatch(event -> matches(event, user, type, resource));
        });
        return server().events().stream().filter(event -> matches(event, user, type, resource)).reduce((first, last) -> last).orElseThrow();
    }

    private static boolean matches(JsonNode event, String user, String type, String resource)
    {
        return user.equals(event.path("user").asText()) && type.equals(event.path("accessType").asText())
                && resource.equals(event.path("resource").asText());
    }

    @Test
    void signsInWithAClientCertificate() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("frank"), List.of("read"), fields("metrics", "*")));
        try (OpenSearchAgent certified = OpenSearchAgent.start(AgentConfig.parse(settings("cert", "gfcert_", "opensearch.tls.cert",
                node().clientCertificate(), "opensearch.tls.key", node().clientKey()), "test"))) {
            eventually("the second agent takes the policies", () -> certified.agent().snapshot() != null);
            certified.syncNow();
            assertThat(exists("gfcert_frank")).isTrue();
        }
        finally {
            node().call("DELETE", "/_plugins/_security/api/rolesmapping/gfcert_frank", "");
            node().call("DELETE", "/_plugins/_security/api/roles/gfcert_frank", "");
        }
    }

    @Test
    void theShadedJarRunsAsAService() throws Exception
    {
        publish(new OpenSearchSnapshots().allow(List.of("erin"), List.of("read"), fields("metrics", "*")));
        Map<String, String> values = settings("jar", "gfjar_", "opensearch.user", OpenSearchNode.ADMIN, "opensearch.password.file",
                file("jar.password", OpenSearchNode.ADMIN_PASSWORD), "sync.interval.ms", "1000");
        StringBuilder properties = new StringBuilder();
        values.forEach((key, value) -> properties.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
        String settingsFile = file("jar.properties", properties.toString());
        Path jar = Path.of(requireNonNull(System.getProperty("grantforge.opensearch.agent.jar"), "grantforge.opensearch.agent.jar"));
        Path output = files.resolve("jar.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(), settingsFile)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            eventually("the jar keeps a role for erin", () -> {
                try {
                    return exists("gfjar_erin");
                }
                catch (IOException | InterruptedException failed) {
                    return false;
                }
            });
        }
        catch (AssertionError late) {
            throw new AssertionError(late.getMessage() + "\n" + Files.readString(output), late);
        }
        finally {
            process.destroy();
            process.waitFor(30, TimeUnit.SECONDS);
            node().admin("DELETE", "/_plugins/_security/api/rolesmapping/gfjar_erin", "");
            node().admin("DELETE", "/_plugins/_security/api/roles/gfjar_erin", "");
        }
        assertThat(Files.readString(output)).contains("keeps OpenSearch " + version());
    }
}
