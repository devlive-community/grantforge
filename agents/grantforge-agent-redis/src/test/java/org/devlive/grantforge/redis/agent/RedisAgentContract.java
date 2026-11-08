// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent against a real primary with a replica of one Redis or Valkey release, its user holding only what the guide
 * tells to give it: keys read, written or both by pattern on both nodes, patterns policies decide differently within
 * withheld, channels, monitoring by node, changes of policies and of the service, selectors changed by hand undone with
 * the operator's root permissions kept, the users it leaves alone, root permissions outside GrantForge found with
 * Redis's own dry runs, the ACL file saved, refusals of the ACL log as access events, and the shaded jar run as a service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class RedisAgentContract
{
    private static final String AGENT = "grantforge";
    private static final String AGENT_PASSWORD = "agent-secret";
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "etl");

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
    private final Logger logger = Logger.getLogger("org.devlive.grantforge.redis.agent");
    private @Nullable RedisNode node;
    private @Nullable AgentServer server;
    private @Nullable RedisAgent agent;
    private long published = 1;

    /**
     * The image under test.
     *
     * @return such as {@code redis:7.2.17}
     */
    abstract String image();

    /**
     * What the agent names the server it keeps.
     *
     * @return such as {@code Redis 7.2.17}
     */
    abstract String release();

    @BeforeAll
    void start() throws Exception
    {
        RedisNode started = new RedisNode(image(), false, new TestCertificates(Files.createDirectory(files.resolve("certificates"))));
        node = started;
        started.everywhere("ACL SETUSER " + AGENT + " on >" + AGENT_PASSWORD + " -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun"
                + " +acl|save +config|get +info +cluster|nodes +command|info");
        for (String user : USERS) {
            started.everywhere("ACL SETUSER " + user + " on >" + user + "-secret -@all +@connection");
        }
        started.everywhere("ACL SETUSER dave %R~hr:* +@read");
        started.everywhere("ACL SETUSER open on nopass -@all +@connection");
        started.everywhere("ACL SETUSER boss on >boss-secret ~* &* +@all");
        started.cli(started.ports().get(0), "MSET sales:1 a cart:1 b orders:1 c hr:public d hr:salary:1 e secret:1 f");
        Thread.sleep(500);
        logger.addHandler(capture);
        server = new AgentServer(new RedisSnapshots());
        agent = RedisAgent.start(AgentConfig.parse(settings("main", "redis.user", AGENT, "redis.password.file", file("agent.password", AGENT_PASSWORD)), "test"));
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

    private RedisNode node()
    {
        return requireNonNull(node, "node");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private RedisAgent agent()
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
    private Map<String, String> settings(String instance, String... more) throws IOException
    {
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + server().port(),
                "grantforge.token.file", file(instance + ".token", AgentServer.TOKEN), "grantforge.instance", "redis-" + instance,
                "grantforge.cache.dir", files.resolve("cache-" + instance).toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.redis.nodes", node().address(), "grantforge.redis.unmanaged.users", "etl"));
        values.putAll(Map.of("grantforge.sync.interval.ms", "3600000", "grantforge.audit.log.interval.ms", "600000", "grantforge.audit.fold.window.ms", "0"));
        for (int index = 0; index < more.length; index += 2) {
            values.put("grantforge." + more[index], more[index + 1]);
        }
        return values;
    }

    private Reconciler.Outcome publish(RedisSnapshots policies) throws InterruptedException
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
            Thread.sleep(100);
        }
    }

    /** Whether Redis lets a user do something on a node: a refusal for lack of a permission is no; any other failure fails the test. */
    private boolean can(String user, int port, Consumer<Jedis> work)
    {
        try (Jedis client = new Jedis(new HostAndPort("127.0.0.1", port), DefaultJedisClientConfig.builder().user(user).password(user + "-secret").build())) {
            work.accept(client);
            return true;
        }
        catch (JedisException refused) {
            if (String.valueOf(refused.getMessage()).startsWith("NOPERM")) {
                return false;
            }
            throw refused;
        }
    }

    private boolean can(String user, Consumer<Jedis> work)
    {
        return can(user, node().ports().get(0), work);
    }

    private String selectors(int port, String user) throws IOException, InterruptedException
    {
        String shown = node().cli(port, "ACL GETUSER " + user);
        int start = shown.indexOf("selectors");
        return start < 0 ? "" : shown.substring(start);
    }

    private boolean warned(String text)
    {
        return logged.stream().anyMatch(record -> String.valueOf(record.getMessage()).contains(text));
    }

    @Test
    void grantsKeysByPatternForReadingWritingOrBothOnEveryNode() throws Exception
    {
        publish(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "sales:*").allow(List.of("bob"), List.of("write"), "key", "cart:*")
                .allow(List.of("carol"), List.of("read", "write"), "key", "orders:*").allow(List.of("carol"), List.of("read"), "key", "*"));
        assertThat(selectors(node().ports().get(0), "carol")).as("every key carol may read, whatever others may").contains("%R~*");
        assertThat(selectors(node().ports().get(0), "alice")).contains("%R~sales:*").contains("-@dangerous", "-scan");
        assertThat(selectors(node().ports().get(1), "alice")).as("the replica keeps an ACL of its own").contains("%R~sales:*");
        assertThat(can("alice", client -> client.get("sales:1"))).isTrue();
        assertThat(can("alice", node().ports().get(1), client -> client.get("sales:1"))).as("read from the replica").isTrue();
        assertThat(can("alice", client -> client.set("sales:2", "x"))).isFalse();
        assertThat(can("alice", client -> client.get("cart:1"))).isFalse();
        assertThat(can("alice", client -> client.scan("0"))).as("listing keys would show those it may not read").isFalse();
        assertThat(can("alice", client -> client.flushAll())).isFalse();
        assertThat(can("bob", client -> client.set("cart:2", "x"))).isTrue();
        assertThat(can("bob", client -> client.get("cart:2"))).isFalse();
        assertThat(can("carol", client -> client.copy("sales:1", "orders:2", true))).as("one selector holds what a command reads and writes").isTrue();
        assertThat(can("carol", client -> client.scan("0"))).as("a user that may read every key may list them").isTrue();
        assertThat(can("carol", client -> client.set("sales:3", "x"))).isFalse();
    }

    @Test
    void withholdsAPatternPoliciesDecideDifferentlyWithin() throws Exception
    {
        publish(new RedisSnapshots().allow(List.of("erin"), List.of("read"), "key", "hr:*").deny(List.of("erin"), List.of("read"), "key", "hr:salary:*")
                .allow(List.of("erin"), List.of("read"), "key", "hr:public"));
        assertThat(can("erin", client -> client.get("hr:public"))).as("a name policies decide alike").isTrue();
        assertThat(can("erin", client -> client.get("hr:salary:1"))).isFalse();
        assertThat(can("erin", client -> client.get("hr:other"))).as("withheld rather than give what a policy denies").isFalse();
        assertThat(warned("GrantForge withholds read of keys hr:* for user erin, as policies decide keys within it differently")).isTrue();
    }

    @Test
    void grantsChannelsAndMonitoringByNode() throws Exception
    {
        publish(new RedisSnapshots().allow(List.of("alice"), List.of("publish"), "channel", "news:*").allow(List.of("bob"), List.of("subscribe"), "channel", "news:*")
                .allow(List.of("alice"), List.of("monitor"), "node", node().address()));
        assertThat(can("alice", client -> client.publish("news:eu", "hi"))).isTrue();
        assertThat(can("bob", client -> client.publish("news:eu", "hi"))).isFalse();
        assertThat(can("alice", client -> client.info())).isTrue();
        assertThat(can("alice", node().ports().get(1), client -> client.info())).as("the policy names the primary alone").isFalse();
    }

    @Test
    void policyAndServiceChangesTakeEffect() throws Exception
    {
        publish(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "sales:*"));
        assertThat(can("alice", client -> client.get("sales:1"))).isTrue();
        Reconciler.Outcome narrowed = publish(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "orders:*"));
        assertThat(narrowed.replaced()).as("on the primary and the replica").isEqualTo(2);
        assertThat(can("alice", client -> client.get("sales:1"))).isFalse();
        assertThat(can("alice", client -> client.get("orders:1"))).isTrue();
        publish(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "orders:*").disabled());
        assertThat(selectors(node().ports().get(0), "alice")).doesNotContain("orders");
        assertThat(can("alice", client -> client.ping())).as("the operator's root permissions stay").isTrue();
        publish(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "orders:*"));
        assertThat(requireNonNull(agent().syncNow(), "outcome").changed()).as("nothing left to change").isFalse();
    }

    @Test
    void undoesSelectorsChangedByHandAndSavesTheAclFile() throws Exception
    {
        publish(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "orders:*"));
        node().everywhere("ACL SETUSER alice (~* +@all)");
        assertThat(can("alice", client -> client.get("secret:1"))).isTrue();
        assertThat(requireNonNull(agent().syncNow(), "outcome").replaced()).isEqualTo(2);
        assertThat(can("alice", client -> client.get("secret:1"))).isFalse();
        assertThat(can("alice", client -> client.get("orders:1"))).isTrue();
        assertThat(node().aclFile()).as("the primary keeps its ACL in a file").contains("user alice", "%R~orders:*");
    }

    @Test
    void leavesItselfSuperusersAndUnmanagedUsersAlone() throws Exception
    {
        publish(new RedisSnapshots().allow(List.of("etl", "boss", AGENT), List.of("read"), "key", "sales:*"));
        assertThat(selectors(node().ports().get(0), "etl")).doesNotContain("sales");
        assertThat(selectors(node().ports().get(0), "boss")).doesNotContain("sales");
        assertThat(selectors(node().ports().get(0), AGENT)).doesNotContain("sales");
        assertThat(warned("Redis user boss may run every command on every key")).isTrue();
        assertThat(warned("Redis user open needs no password")).isTrue();
    }

    @Test
    void asksRedisWhatPermissionsOutsideGrantForgeAllow() throws Exception
    {
        RedisSnapshots policies = new RedisSnapshots();
        long denial = policies.nextId();
        publish(policies.deny(List.of("dave"), List.of("read"), "key", "hr:*"));
        assertThat(warned("Redis user dave may read key hr:* through permissions outside GrantForge, which GrantForge policy " + denial + " denies")).isTrue();
        assertThat(can("dave", client -> client.get("hr:public"))).isTrue();
    }

    @Test
    void reportsRefusalsFromTheAclLogsOfEveryNode() throws Exception
    {
        RedisSnapshots policies = new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "sales:*")
                .allow(List.of("carol"), List.of("publish"), "channel", "news:*");
        long denial = policies.nextId();
        publish(policies.deny(List.of("alice"), List.of("read"), "key", "secret:*"));
        agent().readLogNow();
        assertThat(can("alice", client -> client.get("secret:1"))).isFalse();
        assertThat(can("carol", node().ports().get(1), client -> client.publish("other", "x"))).isFalse();
        assertThat(can("alice", node().ports().get(1), client -> client.info())).isFalse();
        JsonNode secret = event("secret:1");
        assertThat(secret.path("user").asText()).isEqualTo("alice");
        assertThat(secret.path("accessType").asText()).isEqualTo("read");
        assertThat(secret.path("outcome").asText()).isEqualTo("DENIED");
        assertThat(secret.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(secret.path("policyId").asText()).isEqualTo(Long.toString(denial));
        assertThat(secret.path("clientIp").asText()).isNotEmpty();
        JsonNode channel = event("other");
        assertThat(channel.path("accessType").asText()).isEqualTo("publish");
        assertThat(channel.path("request").asText()).as("refused on the replica").contains(node().addresses().get(1));
        JsonNode monitor = event(node().addresses().get(1));
        assertThat(monitor.path("accessType").asText()).isEqualTo("monitor");
        assertThat(monitor.path("resourceType").asText()).isEqualTo("node");
        assertThat(server().events()).noneMatch(event -> AGENT.equals(event.path("user").asText()));
    }

    private JsonNode event(String resource) throws InterruptedException
    {
        eventually("an event of " + resource + " arrives", () -> agent().readLogNow() >= 0 && server().events().stream()
                .anyMatch(event -> resource.equals(event.path("resource").asText())));
        return server().events().stream().filter(event -> resource.equals(event.path("resource").asText())).findFirst().orElseThrow();
    }

    @Test
    void theShadedJarRunsAsAService() throws Exception
    {
        publish(new RedisSnapshots().allow(List.of("erin"), List.of("read"), "key", "jar:*"));
        node().everywhere("ACL SETUSER erin clearselectors");
        Map<String, String> values = settings("jar", "redis.user", AGENT, "redis.password.file", file("jar.password", AGENT_PASSWORD), "sync.interval.ms", "1000");
        StringBuilder properties = new StringBuilder();
        values.forEach((key, value) -> properties.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
        String settingsFile = file("jar.properties", properties.toString());
        Path jar = Path.of(requireNonNull(System.getProperty("grantforge.redis.agent.jar"), "grantforge.redis.agent.jar"));
        Path output = files.resolve("jar.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(), settingsFile)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            eventually("the jar gives erin her selector", () -> {
                try {
                    return selectors(node().ports().get(0), "erin").contains("jar:*");
                }
                catch (IOException | InterruptedException unread) {
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
        }
        assertThat(Files.readString(output)).contains("keeps " + release() + " on 2 nodes");
    }
}
