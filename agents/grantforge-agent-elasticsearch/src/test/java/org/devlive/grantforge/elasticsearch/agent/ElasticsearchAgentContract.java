// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
 * The agent against a real Elasticsearch cluster of one release with security on: reading by index and by field
 * through field level security, native users' roles as groups, the role given to native users among their roles and to
 * others through role mappings, index patterns granted as patterns so that indices created later are reachable at
 * once, and by name where a policy decides otherwise within them, document level security and row filters
 * Elasticsearch would not run, the cluster's own operations, changes of policies and of the service, changes made by
 * hand undone, the users it leaves alone, the audit log files as access events with roles outside GrantForge told apart
 * and reported, sign-in with a client certificate and an API key, the shaded jar run as a service, and, last, a basic
 * license, under which no read that needs field or document level security is granted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class ElasticsearchAgentContract
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> USERS = List.of("alice", "bob", "erin", "etl", "writer", "frank");

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
    private final Logger logger = Logger.getLogger("org.devlive.grantforge.elasticsearch.agent");
    private @Nullable ElasticsearchNode node;
    private @Nullable AgentServer server;
    private @Nullable ElasticsearchAgent agent;
    private long published = 1;

    /**
     * The release under test.
     *
     * @return such as {@code 9.5.5}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        ElasticsearchNode started = new ElasticsearchNode(version(), new TestCertificates(Files.createDirectories(files.resolve("tls"))),
                Files.createDirectories(files.resolve("logs")));
        node = started;
        started.call("PUT", "/_security/role/analysts", "{}");
        started.call("PUT", "/_security/role/dave_reader", "{\"indices\": [{\"names\": [\"hr\"], \"privileges\": [\"read\"]}]}");
        for (String user : USERS) {
            started.user(user);
        }
        started.user("carol", "analysts");
        started.user("dave", "dave_reader");
        started.user("root", ServerState.SUPERUSER);
        started.call("PUT", "/_security/role_mapping/client", "{\"enabled\": true, \"roles\": [\"superuser\"], \"rules\": {\"field\": {\"username\": \""
                + ElasticsearchNode.CERTIFICATE_USER + "\"}}}");
        started.call("PUT", "/logs-eu", "{\"mappings\": {\"properties\": {\"region\": {\"type\": \"keyword\"}, \"card\": {\"type\": \"keyword\"},"
                + " \"name\": {\"type\": \"text\", \"fields\": {\"keyword\": {\"type\": \"keyword\"}}}}}}");
        started.call("PUT", "/logs-eu/_doc/1?refresh=true", "{\"region\": \"EU\", \"card\": \"4111111111111111\", \"name\": \"Anna\"}");
        started.call("PUT", "/logs-eu/_doc/2?refresh=true", "{\"region\": \"APAC\", \"card\": \"340000000000009\", \"name\": \"Chen\"}");
        started.call("PUT", "/logs-us/_doc/1?refresh=true", "{\"region\": \"US\", \"card\": \"5500000000000004\", \"name\": \"Bob\"}");
        started.call("PUT", "/metrics/_doc/1?refresh=true", "{\"cpu\": 0.5}");
        started.call("PUT", "/hr/_doc/1?refresh=true", "{\"employee\": \"ann\", \"salary\": 1000}");
        started.call("PUT", "/team-a/_doc/1?refresh=true", "{\"task\": \"plan\"}");
        logger.addHandler(capture);
        server = new AgentServer(new ElasticsearchSnapshots());
        agent = ElasticsearchAgent.start(AgentConfig.parse(settings("main", "grantforge_", "elasticsearch.user", ElasticsearchNode.ADMIN,
                "elasticsearch.password.file", file("agent.password", ElasticsearchNode.ADMIN_PASSWORD), "audit.log.files",
                started.auditLog().toString()), "test"));
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

    private ElasticsearchNode node()
    {
        return requireNonNull(node, "node");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private ElasticsearchAgent agent()
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
                "grantforge.token.file", file(instance + ".token", AgentServer.TOKEN), "grantforge.instance", "elasticsearch-" + instance,
                "grantforge.cache.dir", files.resolve("cache-" + instance).toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.elasticsearch.hosts", node().url(), "grantforge.elasticsearch.role.prefix", prefix));
        values.putAll(Map.of("grantforge.elasticsearch.unmanaged.users", "etl", "grantforge.sync.interval.ms", "3600000",
                "grantforge.audit.log.interval.ms", "600000", "grantforge.audit.fold.window.ms", "0", "grantforge.elasticsearch.tls.ca",
                node().caCertificate()));
        for (int index = 0; index < more.length; index += 2) {
            values.put("grantforge." + more[index], more[index + 1]);
        }
        return values;
    }

    /** Publishes policies, waits until the agent has them and syncs. */
    private Reconciler.Outcome publish(ElasticsearchSnapshots policies) throws InterruptedException
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
        ElasticsearchNode.Result result = node().as(user, "GET", "/" + index + "/_search?sort=_doc", "");
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
        return JSON.readTree(node().call("GET", "/_security/role/" + name, "")).path(name);
    }

    private boolean exists(String role) throws IOException, InterruptedException
    {
        return node().admin("GET", "/_security/role/" + role, "").ok();
    }

    private List<String> rolesOf(String user) throws IOException, InterruptedException
    {
        List<String> roles = new ArrayList<>();
        JSON.readTree(node().call("GET", "/_security/user/" + user, "")).path(user).path("roles").forEach(role -> roles.add(role.asText()));
        return roles;
    }

    private boolean warned(String text)
    {
        return logged.stream().anyMatch(record -> String.valueOf(record.getMessage()).contains(text));
    }

    @Test
    void grantsReadingByIndexAndByFieldAndThroughRoles() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-*", "*"))
                .allow(List.of("bob"), List.of("read"), fields("hr", "*")).deny(List.of("bob"), List.of("read"), fields("hr", "salary"))
                .allow(List.of("@analysts"), List.of("read"), fields("metrics", "*")));

        assertThat(search("alice", "logs-eu")).hasSize(2);
        assertThat(search("alice", "logs-*")).hasSize(3);
        assertThat(denied("alice", "GET", "/metrics/_search", "")).isTrue();
        assertThat(role("grantforge_alice").path("indices").path(0).path("names").toString()).as("the pattern stays a pattern")
                .isEqualTo("[\"logs-*\"]");
        assertThat(rolesOf("alice")).as("a native user holds the role among its own").containsExactly("grantforge_alice");
        JsonNode mapping = JSON.readTree(node().call("GET", "/_security/role_mapping/grantforge_alice", "")).path("grantforge_alice");
        assertThat(mapping.path("rules").path("field").path("username").asText()).as("other realms get it by name").isEqualTo("alice");

        List<JsonNode> bob = search("bob", "hr");
        assertThat(bob).hasSize(1).allMatch(source -> !source.has("salary") && source.has("employee"));
        assertThat(role("grantforge_bob").path("indices").path(0).path("field_security").path("grant").toString())
                .isEqualTo("[\"employee\",\"employee.*\"]");

        assertThat(search("carol", "metrics")).as("through the native role analysts").hasSize(1);
        assertThat(rolesOf("carol")).containsExactlyInAnyOrder("analysts", "grantforge_carol");
        assertThat(denied("carol", "GET", "/logs-eu/_search", "")).isTrue();
    }

    @Test
    void readsFieldsWithTheirMultiFields() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("erin"), List.of("read"), fields("logs-eu", "name"))
                .allow(List.of("erin"), List.of("read"), fields("logs-eu", "region")));
        List<JsonNode> eu = search("erin", "logs-eu");
        assertThat(eu).hasSize(2).allMatch(source -> source.has("name") && !source.has("card"));
        ElasticsearchNode.Result keyword = node().as("erin", "POST", "/logs-eu/_search", "{\"query\": {\"term\": {\"name.keyword\": \"Anna\"}}}");
        assertThat(keyword.json().path("hits").path("hits")).as("name.keyword is read with name: " + keyword.body()).hasSize(1);
    }

    @Test
    void patternsReachIndicesCreatedLaterAndNamesWhereAPolicyDecidesOtherwise() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("writer"), List.of("index", "create_index"), "index", "app-logs-*")
                .allow(List.of("writer"), List.of("read"), fields("app-logs-*", "*"))
                .allow(List.of("frank"), List.of("index", "create_index"), "index", "team-*")
                .deny(List.of("frank"), List.of("index"), "index", "team-secret"));

        ElasticsearchNode.Result created = node().as("writer", "POST", "/app-logs-2027/_doc?refresh=true", "{\"message\": \"tomorrow\"}");
        assertThat(created.status()).as("an index created after the sync: " + created.body()).isEqualTo(201);
        assertThat(search("writer", "app-logs-2027")).hasSize(1);

        assertThat(node().as("frank", "POST", "/team-a/_doc", "{\"task\": \"build\"}").status()).isEqualTo(201);
        assertThat(role("grantforge_frank").path("indices").toString()).as("team-* holds team-secret, which a policy decides otherwise")
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
    void filtersDocuments() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("erin"), List.of("read"), fields("logs-eu", "*"))
                .allow(List.of("erin"), List.of("read"), fields("logs-us", "*"))
                .filter(List.of("erin"), "{\"term\": {\"region\": \"EU\"}}", "logs-eu").filter(List.of("erin"), "{\"bogus\": {}}", "logs-us"));

        List<JsonNode> eu = search("erin", "logs-eu");
        assertThat(eu).hasSize(1);
        assertThat(eu.get(0).path("region").asText()).isEqualTo("EU");
        assertThat(search("erin", "logs-us")).as("Elasticsearch would not run the query, so no document shows").isEmpty();
        assertThat(warned("with a query Elasticsearch would not run")).isTrue();
    }

    @Test
    void grantsTheClustersOwnOperations() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("erin"), List.of("cluster_monitor"), "cluster", "*")
                .allow(List.of("alice"), List.of("read"), fields("logs-us", "*")));
        assertThat(node().as("erin", "GET", "/_cluster/health", "").ok()).isTrue();
        assertThat(denied("alice", "GET", "/_cluster/health", "")).isTrue();
        ElasticsearchNode.Result searched = node().as("alice", "POST", "/_msearch", "{\"index\": \"logs-us\"}\n{\"query\": {\"match_all\": {}}}\n");
        assertThat(searched.ok()).as("reading needs no cluster privilege for multi-search: " + searched.body()).isTrue();
    }

    @Test
    void policyAndServiceChangesTakeEffectAndRolesGo() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")));
        assertThat(search("alice", "logs-us")).hasSize(1);
        Reconciler.Outcome off = publish(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")).disabled());
        assertThat(off.dropped()).isPositive();
        assertThat(exists("grantforge_alice")).isFalse();
        assertThat(rolesOf("alice")).as("the role is taken back before it goes").isEmpty();
        assertThat(node().admin("GET", "/_security/role_mapping/grantforge_alice", "").ok()).isFalse();
        assertThat(denied("alice", "GET", "/logs-us/_search", "")).isTrue();
        assertThat(warned("is not in use")).isTrue();
        publish(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")).serviceType("hive"));
        assertThat(warned("not an Elasticsearch one")).isTrue();
        assertThat(exists("grantforge_alice")).isFalse();
    }

    @Test
    void undoesChangesMadeByHand() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-us", "*")));
        node().call("PUT", "/_security/role/grantforge_alice", "{\"description\": \"GrantForge agent role of user alice\", \"metadata\":"
                + " {\"grantforge_user\": \"alice\"}, \"indices\": [{\"names\": [\"hr\"], \"privileges\": [\"read\"]}]}");
        node().call("PUT", "/_security/user/bob", "{\"password\": \"" + ElasticsearchNode.PASSWORD + "\", \"roles\": [\"grantforge_alice\"]}");
        assertThat(search("bob", "hr")).hasSize(1);
        Reconciler.Outcome restored = requireNonNull(agent().syncNow(), "outcome");
        assertThat(restored.updated()).isEqualTo(1);
        assertThat(restored.assigned()).isPositive();
        assertThat(rolesOf("bob")).as("another user's role is taken back").isEmpty();
        assertThat(denied("alice", "GET", "/hr/_search", "")).isTrue();
        assertThat(denied("bob", "GET", "/hr/_search", "")).isTrue();
        assertThat(search("alice", "logs-us")).hasSize(1);
    }

    @Test
    void leavesItselfSuperusersAndUnmanagedUsersAlone() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("etl", "root", ElasticsearchNode.ADMIN), List.of("read"), fields("*", "*")));
        assertThat(exists("grantforge_etl")).isFalse();
        assertThat(exists("grantforge_root")).isFalse();
        assertThat(exists("grantforge_elastic")).isFalse();
        assertThat(rolesOf("root")).containsExactly(ServerState.SUPERUSER);
        assertThat(warned("Elasticsearch user root has the superuser role")).isTrue();
    }

    @Test
    void reportsAccessesFromTheAuditLogAndRolesOutsideGrantForge() throws Exception
    {
        ElasticsearchSnapshots policies = new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-*", "*"));
        long denial = policies.nextId();
        policies.deny(List.of("bob"), List.of("read"), fields("logs-us", "*"));
        long outside = policies.nextId();
        policies.deny(List.of("dave"), List.of("read"), fields("hr", "*"));
        publish(policies);
        assertThat(warned("Elasticsearch role dave_reader, which user dave has outside GrantForge, gives read on hr, which GrantForge policy " + outside
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
        assertThat(server().events()).noneMatch(event -> ElasticsearchNode.ADMIN.equals(event.path("user").asText()));
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
        publish(new ElasticsearchSnapshots().allow(List.of("frank"), List.of("read"), fields("metrics", "*")));
        try (ElasticsearchAgent certified = ElasticsearchAgent.start(AgentConfig.parse(settings("cert", "gfcert_", "elasticsearch.tls.cert",
                node().clientCertificate(), "elasticsearch.tls.key", node().clientKey()), "test"))) {
            eventually("the second agent takes the policies", () -> certified.agent().snapshot() != null);
            certified.syncNow();
            assertThat(exists("gfcert_frank")).isTrue();
        }
        finally {
            cleanUp("gfcert_frank");
        }
    }

    @Test
    void signsInWithAnApiKey() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("frank"), List.of("read"), fields("metrics", "*")));
        String key = JSON.readTree(node().call("POST", "/_security/api_key", "{\"name\": \"grantforge-agent\"}")).path("encoded").asText();
        try (ElasticsearchAgent keyed = ElasticsearchAgent.start(AgentConfig.parse(settings("key", "gfkey_", "elasticsearch.api.key.file",
                file("agent.key", key)), "test"))) {
            eventually("the third agent takes the policies", () -> keyed.agent().snapshot() != null);
            keyed.syncNow();
            assertThat(exists("gfkey_frank")).isTrue();
        }
        finally {
            cleanUp("gfkey_frank");
        }
    }

    /** Takes a role of another agent away from frank and removes it with its mapping. */
    private void cleanUp(String role) throws IOException, InterruptedException
    {
        List<String> roles = rolesOf("frank");
        roles.remove(role);
        node().call("PUT", "/_security/user/frank", "{\"roles\": " + JSON.writeValueAsString(roles) + "}");
        node().admin("DELETE", "/_security/role_mapping/" + role, "");
        node().admin("DELETE", "/_security/role/" + role, "");
    }

    @Test
    void theShadedJarRunsAsAService() throws Exception
    {
        publish(new ElasticsearchSnapshots().allow(List.of("frank"), List.of("read"), fields("metrics", "*")));
        Map<String, String> values = settings("jar", "gfjar_", "elasticsearch.user", ElasticsearchNode.ADMIN, "elasticsearch.password.file",
                file("jar.password", ElasticsearchNode.ADMIN_PASSWORD), "sync.interval.ms", "1000");
        StringBuilder properties = new StringBuilder();
        values.forEach((key, value) -> properties.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
        String settingsFile = file("jar.properties", properties.toString());
        Path jar = Path.of(requireNonNull(System.getProperty("grantforge.elasticsearch.agent.jar"), "grantforge.elasticsearch.agent.jar"));
        Path output = files.resolve("jar.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(), settingsFile)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            eventually("the jar keeps a role for frank", () -> {
                try {
                    return exists("gfjar_frank");
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
            cleanUp("gfjar_frank");
        }
        assertThat(Files.readString(output)).contains("keeps Elasticsearch " + version());
    }

    /** Last: a basic license cannot go back to the trial, and turns off what needs Gold and above. */
    @Test
    @Order(Integer.MAX_VALUE)
    void belowPlatinumGrantsNoReadThatNeedsFieldOrDocumentLevelSecurity() throws Exception
    {
        node().call("POST", "/_license/start_basic?acknowledge=true", "");
        publish(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), fields("logs-*", "*"))
                .allow(List.of("bob"), List.of("read", "index"), fields("hr", "*")).deny(List.of("bob"), List.of("read"), fields("hr", "salary"))
                .allow(List.of("erin"), List.of("read"), fields("metrics", "*")).filter(List.of("erin"), "{\"range\": {\"cpu\": {\"gte\": 1}}}", "metrics"));
        assertThat(search("alice", "logs-eu")).as("a whole index needs neither").hasSize(2);
        assertThat(denied("bob", "GET", "/hr/_search", "")).as("bob may read some fields of hr only").isTrue();
        assertThat(role("grantforge_bob").path("indices").path(0).path("privileges").toString()).isEqualTo("[\"index\"]");
        assertThat(denied("erin", "GET", "/metrics/_search", "")).as("erin may read some documents of metrics only").isTrue();
        assertThat(warned("the cluster's license is below Platinum")).isTrue();
        assertThat(warned("has no document and field level security")).isTrue();
    }
}
