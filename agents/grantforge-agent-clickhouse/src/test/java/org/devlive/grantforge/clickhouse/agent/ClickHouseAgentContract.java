// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import com.fasterxml.jackson.databind.JsonNode;
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
 * The agent against a real ClickHouse server of one release: privileges down to columns and on whole databases, table,
 * database and server privileges, ClickHouse roles as groups, row filters as row policies, changes of policies and of
 * the service, changes made by hand undone, the users it leaves alone, names that need quoting, a row filter that would
 * widen access, the query log as access events with grants outside GrantForge told apart and reported, sign-in with a
 * client certificate over https, and the shaded jar run as a service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class ClickHouseAgentContract
{
    private static final String AGENT = "grantforge";
    private static final String AGENT_PASSWORD = "agent-secret";
    private static final String ODD = "o'brien \"x\"";
    /** What the agent's user needs, as the agent's guide says: what it grants, with the grant option, and access management. */
    private static final String AGENT_PRIVILEGES = "SELECT, INSERT, ALTER, CREATE TABLE, CREATE VIEW, CREATE DICTIONARY, CREATE DATABASE, DROP TABLE,"
            + " DROP VIEW, DROP DICTIONARY, DROP DATABASE, TRUNCATE, OPTIMIZE, KILL QUERY, SYSTEM";
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "etl");

    /** The certificates and the agents' files; the server starts before the instance's own directories exist. */
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
    private final Logger logger = Logger.getLogger("org.devlive.grantforge.clickhouse.agent");
    private @Nullable ClickHouseNode node;
    private @Nullable AgentServer server;
    private @Nullable ClickHouseAgent agent;
    private long published = 1;

    /**
     * The release under test.
     *
     * @return such as {@code 26.8.34.13}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        ClickHouseNode started = new ClickHouseNode(version(), Files.createDirectories(files.resolve("tls")));
        node = started;
        started.run("CREATE DATABASE sales", "CREATE DATABASE hr",
                "CREATE TABLE sales.orders (id UInt32, region String, card String, amount Float64) ENGINE = MergeTree ORDER BY id",
                "INSERT INTO sales.orders VALUES (1, 'EU', '4111', 10), (2, 'US', '5500', 20), (3, 'EU', '3400', 30)",
                "CREATE TABLE sales.items (order_id UInt32, sku String) ENGINE = MergeTree ORDER BY order_id",
                "CREATE TABLE sales.`odd-table` (`we ird` UInt8, `it's` UInt8) ENGINE = MergeTree ORDER BY `we ird`",
                "INSERT INTO sales.`odd-table` VALUES (1, 2)",
                "CREATE TABLE hr.salaries (employee String, amount Float64) ENGINE = MergeTree ORDER BY employee",
                "INSERT INTO hr.salaries VALUES ('ann', 1000)",
                "CREATE TABLE hr.people (name String) ENGINE = MergeTree ORDER BY name", "INSERT INTO hr.people VALUES ('ann')",
                "CREATE USER " + AGENT + " IDENTIFIED BY '" + AGENT_PASSWORD + "'", "GRANT " + AGENT_PRIVILEGES + " ON *.* TO " + AGENT + " WITH GRANT OPTION",
                "GRANT ACCESS MANAGEMENT ON *.* TO " + AGENT,
                "CREATE USER " + ClickHouseNode.CERTIFICATE_USER + " IDENTIFIED WITH ssl_certificate CN '" + ClickHouseNode.CERTIFICATE_USER + "'",
                "GRANT " + AGENT_PRIVILEGES + " ON *.* TO " + ClickHouseNode.CERTIFICATE_USER + " WITH GRANT OPTION",
                "GRANT ACCESS MANAGEMENT ON *.* TO " + ClickHouseNode.CERTIFICATE_USER,
                "CREATE USER `" + ODD.replace("`", "\\`") + "` IDENTIFIED BY 'odd-secret'",
                "CREATE ROLE analysts", "CREATE ROLE readers", "GRANT readers TO analysts");
        for (String user : USERS) {
            started.run("CREATE USER " + user + " IDENTIFIED BY '" + user + "-secret'");
        }
        started.run("GRANT analysts TO carol", "GRANT SELECT ON hr.salaries TO dave");
        logger.addHandler(capture);
        server = new AgentServer(new ClickHouseSnapshots());
        agent = ClickHouseAgent.start(AgentConfig.parse(settings("main", "grantforge_", node().url(), "clickhouse.user", AGENT,
                "clickhouse.password.file", file("agent.password", AGENT_PASSWORD)), "test"));
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

    private ClickHouseNode node()
    {
        return requireNonNull(node, "node");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private ClickHouseAgent agent()
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
    private Map<String, String> settings(String instance, String prefix, String url, String... more) throws IOException
    {
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + server().port(),
                "grantforge.token.file", file(instance + ".token", AgentServer.TOKEN), "grantforge.instance", "clickhouse-" + instance,
                "grantforge.cache.dir", files.resolve("cache-" + instance).toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.clickhouse.urls", url, "grantforge.clickhouse.role.prefix", prefix));
        values.putAll(Map.of("grantforge.clickhouse.unmanaged.users", "etl", "grantforge.sync.interval.ms", "3600000",
                "grantforge.audit.query.log.delay.ms", "0", "grantforge.audit.query.log.interval.ms", "600000", "grantforge.audit.fold.window.ms", "0"));
        for (int index = 0; index < more.length; index += 2) {
            values.put("grantforge." + more[index], more[index + 1]);
        }
        return values;
    }

    /** Publishes policies, waits until the agent has them and syncs. */
    private Reconciler.Outcome publish(ClickHouseSnapshots policies) throws InterruptedException
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

    private ClickHouseNode.Result as(String user, String statement) throws IOException, InterruptedException
    {
        return node().as(user, ODD.equals(user) ? "odd-secret" : user + "-secret", statement);
    }

    private String rows(String user, String statement) throws IOException, InterruptedException
    {
        ClickHouseNode.Result result = as(user, statement);
        assertThat(result.ok()).as(user + ": " + statement + ": " + result.error()).isTrue();
        return result.output();
    }

    private boolean warned(String text)
    {
        return logged.stream().anyMatch(record -> String.valueOf(record.getMessage()).contains(text));
    }

    private static String[] column(String database, String table, String column)
    {
        return new String[] {"database", database, "table", table, "column", column};
    }

    @Test
    void grantsReadingOnWholeDatabasesTablesAndColumns() throws Exception
    {
        publish(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .allow(List.of("bob"), List.of("select"), column("sales", "orders", "*"))
                .deny(List.of("bob"), List.of("select"), column("sales", "orders", "card"))
                .allow(List.of("@analysts"), List.of("select"), column("hr", "*", "*"))
                .deny(List.of("carol"), List.of("select"), column("hr", "salaries", "*")));

        assertThat(rows("alice", "SELECT count() FROM sales.orders")).isEqualTo("3");
        assertThat(node().value("SHOW GRANTS FOR grantforge_alice")).contains("GRANT SELECT ON sales.* TO grantforge_alice");
        node().run("CREATE TABLE sales.returns (id UInt32) ENGINE = MergeTree ORDER BY id");
        try {
            assertThat(rows("alice", "SELECT count() FROM sales.returns")).as("a table created later is read at once").isEqualTo("0");
        }
        finally {
            node().run("DROP TABLE sales.returns");
        }
        assertThat(as("alice", "SELECT count() FROM hr.people").denied()).isTrue();

        assertThat(rows("bob", "SELECT id, region FROM sales.orders ORDER BY id")).startsWith("1\tEU");
        assertThat(as("bob", "SELECT card FROM sales.orders").denied()).isTrue();
        assertThat(as("bob", "SELECT * FROM sales.items").denied()).isTrue();

        assertThat(rows("carol", "SELECT name FROM hr.people")).as("through the ClickHouse role analysts").isEqualTo("ann");
        assertThat(as("carol", "SELECT * FROM hr.salaries").denied()).isTrue();
        assertThat(node().value("SHOW GRANTS FOR grantforge_carol")).contains("hr.people").doesNotContain("hr.salaries", "hr.*");
    }

    @Test
    void grantsTableDatabaseAndServerPrivileges() throws Exception
    {
        ClickHouseSnapshots policies = new ClickHouseSnapshots().allow(List.of("alice"), List.of("create"), "database", "team_*")
                .allow(List.of("alice"), List.of("alter", "drop", "truncate", "optimize"), "database", "team_*", "table", "*")
                .allow(List.of("alice"), List.of("select", "insert"), column("team_*", "*", "*"))
                .allow(List.of("alice"), List.of("create_database"), "server", "*");
        publish(policies);
        try {
            assertThat(as("alice", "CREATE DATABASE team_a").ok()).isTrue();
            assertThat(as("bob", "CREATE DATABASE team_b").denied()).isTrue();
            assertThat(as("alice", "CREATE TABLE team_a.t (x UInt8) ENGINE = MergeTree ORDER BY x").denied())
                    .as("the new database's privileges come with the next sync").isTrue();
            agent().syncNow();
            assertThat(rows("alice", "CREATE TABLE team_a.t (x UInt8) ENGINE = MergeTree ORDER BY x")).isEmpty();
            assertThat(rows("alice", "INSERT INTO team_a.t VALUES (1)")).isEmpty();
            assertThat(rows("alice", "SELECT x FROM team_a.t")).isEqualTo("1");
            assertThat(rows("alice", "ALTER TABLE team_a.t ADD COLUMN y UInt8 DEFAULT 2")).isEmpty();
            assertThat(rows("alice", "OPTIMIZE TABLE team_a.t")).isEmpty();
            assertThat(rows("alice", "TRUNCATE TABLE team_a.t")).isEmpty();
            assertThat(rows("alice", "DROP TABLE team_a.t")).isEmpty();
            assertThat(as("alice", "DROP DATABASE team_a").denied()).isTrue();
        }
        finally {
            node().run("DROP DATABASE IF EXISTS team_a");
        }
    }

    @Test
    void rowFiltersBecomeRowPoliciesOfTheUsersRole() throws Exception
    {
        ClickHouseSnapshots base = new ClickHouseSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*"));
        publish(base.filter(List.of("alice"), "region = 'EU'", "sales", "orders"));
        assertThat(rows("alice", "SELECT count() FROM sales.orders")).isEqualTo("2");
        assertThat(rows("bob", "SELECT count() FROM sales.orders")).as("the filter is alice's").isEqualTo("3");
        assertThat(node().value("SELECT apply_to_list FROM system.row_policies WHERE short_name = 'grantforge_alice'"))
                .isEqualTo("['grantforge_alice']");

        publish(new ClickHouseSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*"))
                .filter(List.of("alice"), "region = 'US'", "sales", "orders"));
        assertThat(rows("alice", "SELECT id FROM sales.orders")).isEqualTo("2");

        publish(new ClickHouseSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*")));
        assertThat(rows("alice", "SELECT count() FROM sales.orders")).isEqualTo("3");
        assertThat(node().value("SELECT count() FROM system.row_policies WHERE startsWith(short_name, 'grantforge_')")).isEqualTo("0");
    }

    @Test
    void aRowFilterThatWouldWidenAccessShowsNoRows() throws Exception
    {
        publish(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .filter(List.of("alice"), "1) TO ALL --", "sales", "orders"));
        assertThat(rows("alice", "SELECT count() FROM sales.orders")).isEqualTo("0");
        assertThat(node().value("SELECT apply_to_all, apply_to_list FROM system.row_policies WHERE short_name = 'grantforge_alice'"))
                .isEqualTo("0\t['grantforge_alice']");
        assertThat(warned("changes whom ClickHouse's row policy applies to")).isTrue();
        Reconciler.Outcome again = requireNonNull(agent().syncNow(), "outcome");
        assertThat(again.rowPolicies()).as("the replacement stays").isZero();

        publish(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .filter(List.of("alice"), "no_such_column = 1", "sales", "orders"));
        ClickHouseNode.Result broken = as("alice", "SELECT count() FROM sales.orders");
        assertThat(broken.ok() ? broken.output() : "refused").as("a filter ClickHouse cannot apply shows nothing").isIn("0", "refused");
    }

    @Test
    void policyAndServiceChangesTakeEffectAndRolesGo() throws Exception
    {
        ClickHouseSnapshots alice = new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"));
        publish(alice);
        assertThat(rows("alice", "SELECT count() FROM sales.orders")).isEqualTo("3");
        assertThat(node().value("SELECT name FROM system.roles WHERE startsWith(name, 'grantforge_')")).isEqualTo("grantforge_alice");

        Reconciler.Outcome none = publish(new ClickHouseSnapshots());
        assertThat(none.dropped()).isEqualTo(1);
        assertThat(as("alice", "SELECT count() FROM sales.orders").denied()).isTrue();

        publish(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*")).disabled());
        assertThat(node().value("SELECT count() FROM system.roles WHERE startsWith(name, 'grantforge_')")).isEqualTo("0");

        publish(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*")).serviceType("hive"));
        assertThat(node().value("SELECT count() FROM system.roles WHERE startsWith(name, 'grantforge_')")).isEqualTo("0");
        assertThat(warned("bound to a hive service")).isTrue();
    }

    @Test
    void undoesChangesMadeByHand() throws Exception
    {
        publish(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*")));
        node().run("GRANT SELECT ON hr.* TO grantforge_alice", "REVOKE grantforge_alice FROM alice");
        assertThat(as("alice", "SELECT count() FROM sales.orders").denied()).isTrue();
        Reconciler.Outcome repaired = requireNonNull(agent().syncNow(), "outcome");
        assertThat(repaired.roles()).isEqualTo(1);
        assertThat(rows("alice", "SELECT count() FROM sales.orders")).isEqualTo("3");
        assertThat(node().value("SHOW GRANTS FOR grantforge_alice")).doesNotContain("hr");

        node().run("SET DEFAULT ROLE NONE TO alice");
        agent().syncNow();
        assertThat(rows("alice", "SELECT count() FROM sales.orders")).as("the role is a default role again").isEqualTo("3");
        assertThat(requireNonNull(agent().syncNow(), "outcome").changed()).as("nothing to do").isFalse();
    }

    @Test
    void leavesItselfUsersXmlAndUnmanagedUsersAlone() throws Exception
    {
        publish(new ClickHouseSnapshots().allow(List.of("@public"), List.of("select"), column("sales", "*", "*")));
        String roles = node().value("SELECT arrayStringConcat(groupArray(name), ',') FROM (SELECT name FROM system.roles"
                + " WHERE startsWith(name, 'grantforge_') ORDER BY name)");
        assertThat(roles.split(",")).contains("grantforge_alice", "grantforge_erin", "grantforge_" + ODD)
                .doesNotContain("grantforge_etl", "grantforge_admin", "grantforge_" + AGENT);
        assertThat(as("etl", "SELECT count() FROM sales.orders").denied()).isTrue();
        assertThat(warned("ClickHouse user admin is stored where nothing can change it")).isTrue();
    }

    @Test
    void namesThatNeedQuotingAreQuoted() throws Exception
    {
        publish(new ClickHouseSnapshots().allow(List.of(ODD), List.of("select"), column("sales", "odd-table", "we ird")));
        assertThat(rows(ODD, "SELECT `we ird` FROM sales.`odd-table`")).isEqualTo("1");
        assertThat(as(ODD, "SELECT `it's` FROM sales.`odd-table`").denied()).isTrue();
    }

    @Test
    void reportsAccessesFromTheQueryLogAndGrantsOutsideGrantForge() throws Exception
    {
        ClickHouseSnapshots policies = new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .allow(List.of("bob"), List.of("select"), column("sales", "orders", "*"));
        long denial = policies.nextId();
        policies.deny(List.of("bob"), List.of("select"), column("sales", "orders", "card"))
                .deny(List.of("dave"), List.of("select"), column("hr", "salaries", "*"));
        publish(policies);
        assertThat(warned("ClickHouse user dave holds SELECT ON hr.salaries directly, outside GrantForge")).isTrue();
        agent().readLogNow();

        rows("alice", "SELECT region FROM sales.orders");
        assertThat(as("bob", "SELECT card FROM sales.orders").denied()).isTrue();
        rows("dave", "SELECT * FROM hr.salaries");
        node().run("SYSTEM FLUSH LOGS");
        eventually("the query log is read", () -> agent().readLogNow() >= 0 && server().events().size() >= 3);

        JsonNode alice = event("alice");
        assertThat(alice.path("resource").asText()).isEqualTo("sales.orders");
        assertThat(alice.path("accessType").asText()).isEqualTo("select");
        assertThat(alice.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(alice.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(alice.path("action").asText()).isEqualTo("SELECT");
        assertThat(alice.path("request").asText()).isEqualTo("SELECT region FROM sales.orders");
        assertThat(alice.path("clientIp").asText()).isNotEmpty();

        JsonNode bob = event("bob");
        assertThat(bob.path("resource").asText()).as("ClickHouse names the column or, with some of its columns granted, the table")
                .isIn("sales.orders.card", "sales.orders");
        assertThat(bob.path("outcome").asText()).isEqualTo("DENIED");
        assertThat(bob.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(bob.path("policyId").asText()).isEqualTo(Long.toString(denial));

        JsonNode dave = event("dave");
        assertThat(dave.path("resource").asText()).isEqualTo("hr.salaries");
        assertThat(dave.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(dave.path("enforcer").asText()).as("a grant outside GrantForge allowed it").isEqualTo("NATIVE");
        assertThat(server().events()).noneMatch(event -> AGENT.equals(event.path("user").asText()));
    }

    private JsonNode event(String user) throws InterruptedException
    {
        eventually("an event of " + user + " arrives", () -> server().events().stream().anyMatch(event -> user.equals(event.path("user").asText())));
        return server().events().stream().filter(event -> user.equals(event.path("user").asText())).reduce((first, last) -> last).orElseThrow();
    }

    @Test
    void signsInWithAClientCertificateOverHttps() throws Exception
    {
        publish(new ClickHouseSnapshots().allow(List.of("erin"), List.of("select"), column("sales", "*", "*")));
        try (ClickHouseAgent secure = ClickHouseAgent.start(AgentConfig.parse(settings("tls", "gftls_", node().secureUrl(), "clickhouse.user",
                ClickHouseNode.CERTIFICATE_USER, "clickhouse.tls.ca", node().caCertificate(), "clickhouse.tls.cert", node().clientCertificate(),
                "clickhouse.tls.key", node().clientKey(), "audit.query.log", "false"), "test"))) {
            eventually("the second agent takes the policies", () -> {
                Snapshot snapshot = secure.agent().snapshot();
                return snapshot != null && snapshot.policyVersion() == published;
            });
            secure.syncNow();
            assertThat(node().value("SHOW GRANTS FOR gftls_erin")).contains("GRANT SELECT ON sales.* TO gftls_erin");
        }
        finally {
            node().run("DROP ROLE IF EXISTS gftls_erin");
        }
    }

    @Test
    void theShadedJarRunsAsAService() throws Exception
    {
        publish(new ClickHouseSnapshots().allow(List.of("erin"), List.of("select"), column("sales", "*", "*")));
        Map<String, String> values = settings("jar", "gfjar_", node().url(), "clickhouse.user", AGENT, "clickhouse.password.file",
                file("jar.password", AGENT_PASSWORD), "sync.interval.ms", "1000");
        StringBuilder properties = new StringBuilder();
        values.forEach((key, value) -> properties.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
        String settingsFile = file("jar.properties", properties.toString());
        Path jar = Path.of(requireNonNull(System.getProperty("grantforge.clickhouse.agent.jar"), "grantforge.clickhouse.agent.jar"));
        Path output = files.resolve("jar.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(), settingsFile)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            eventually("the jar keeps a role for erin", () -> {
                try {
                    return "1".equals(node().value("SELECT count() FROM system.roles WHERE name = 'gfjar_erin'"));
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
            node().run("DROP ROLE IF EXISTS gfjar_erin");
        }
        assertThat(Files.readString(output)).contains("keeps ClickHouse " + version());
    }
}
