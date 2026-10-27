// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

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
import java.sql.SQLException;
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
 * The agent against a real Doris cluster of one release: reading down to columns and on whole databases, loading,
 * altering, dropping, creating and showing views, workload groups, Doris roles as groups, row filters as row policies
 * and their changes, a row filter Doris refuses, changes of policies and of the service, changes made by hand undone,
 * the users it leaves alone, the identities of one user, the audit log as access events with grants outside GrantForge
 * told apart and reported, and the shaded jar run as a service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 15, unit = TimeUnit.MINUTES)
abstract class DorisAgentContract
{
    private static final String AGENT = "grantforge";
    private static final String AGENT_PASSWORD = "agent-secret";
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "etl", "admin2");

    /** The agents' files. */
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
    private final Logger logger = Logger.getLogger("org.devlive.grantforge.doris.agent");
    private @Nullable DorisNode node;
    private @Nullable AgentServer server;
    private @Nullable DorisAgent agent;
    private long published = 1;

    /**
     * The release under test.
     *
     * @return such as {@code 4.1.5}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        DorisNode started = new DorisNode(version());
        node = started;
        String table = " DUPLICATE KEY(%s) DISTRIBUTED BY HASH(%s) BUCKETS 1 PROPERTIES ('replication_num' = '1')";
        started.run("CREATE DATABASE sales", "CREATE DATABASE hr",
                "CREATE TABLE sales.orders (id INT, region VARCHAR(8), card VARCHAR(8), amount DOUBLE)" + table.formatted("id", "id"),
                "INSERT INTO sales.orders VALUES (1, 'EU', '4111', 10), (2, 'US', '5500', 20), (3, 'EU', '3400', 30)",
                "CREATE TABLE sales.items (order_id INT, sku VARCHAR(8))" + table.formatted("order_id", "order_id"),
                "CREATE VIEW sales.eu AS SELECT id FROM sales.orders WHERE region = 'EU'",
                "CREATE TABLE hr.salaries (employee VARCHAR(8), amount DOUBLE)" + table.formatted("employee", "employee"),
                "INSERT INTO hr.salaries VALUES ('ann', 1000)",
                "CREATE TABLE hr.people (name VARCHAR(8))" + table.formatted("name", "name"), "INSERT INTO hr.people VALUES ('ann')",
                "CREATE USER '" + AGENT + "'@'%' IDENTIFIED BY '" + AGENT_PASSWORD + "'", "GRANT ADMIN_PRIV ON *.*.* TO '" + AGENT + "'@'%'",
                "CREATE ROLE analysts", "SET GLOBAL enable_audit_plugin = true", "SET GLOBAL audit_plugin_max_batch_interval_sec = 1");
        for (String user : USERS) {
            started.run("CREATE USER '" + user + "'@'%' IDENTIFIED BY '" + user + "-secret'");
        }
        started.run("CREATE USER 'erin'@'10.%' IDENTIFIED BY 'erin-other'", "GRANT 'analysts' TO 'carol'@'%'",
                "GRANT SELECT_PRIV ON internal.hr.salaries TO 'dave'@'%'", "GRANT ADMIN_PRIV ON *.*.* TO 'admin2'@'%'");
        logger.addHandler(capture);
        server = new AgentServer(new DorisSnapshots());
        agent = DorisAgent.start(AgentConfig.parse(settings("main", "grantforge_", "doris.user", AGENT, "doris.password.file",
                file("agent.password", AGENT_PASSWORD)), "test"));
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

    private DorisNode node()
    {
        return requireNonNull(node, "node");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private DorisAgent agent()
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
                "grantforge.token.file", file(instance + ".token", AgentServer.TOKEN), "grantforge.instance", "doris-" + instance,
                "grantforge.cache.dir", files.resolve("cache-" + instance).toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.doris.hosts", node().host(), "grantforge.doris.role.prefix", prefix));
        values.putAll(Map.of("grantforge.doris.unmanaged.users", "etl", "grantforge.sync.interval.ms", "3600000",
                "grantforge.audit.log.delay.ms", "2000", "grantforge.audit.log.interval.ms", "600000", "grantforge.audit.fold.window.ms", "0"));
        for (int index = 0; index < more.length; index += 2) {
            values.put("grantforge." + more[index], more[index + 1]);
        }
        return values;
    }

    /** Publishes policies, waits until the agent has them and syncs. */
    private Reconciler.Outcome publish(DorisSnapshots policies) throws InterruptedException
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
        Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(200);
        }
    }

    private DorisNode.Result as(String user, String statement)
    {
        return node().as(user, user + "-secret", statement);
    }

    private String rows(String user, String statement)
    {
        DorisNode.Result result = as(user, statement);
        assertThat(result.ok()).as(user + ": " + statement + ": " + result.error()).isTrue();
        return result.output();
    }

    private boolean warned(String text)
    {
        return logged.stream().anyMatch(record -> String.valueOf(record.getMessage()).contains(text));
    }

    private String role(String name) throws SQLException
    {
        for (List<String> row : node().rows("SHOW ROLES")) {
            if (row.get(0).equals(name)) {
                return String.join(" | ", row);
            }
        }
        return "";
    }

    private static String[] column(String database, String table, String column)
    {
        return new String[] {"catalog", "internal", "database", database, "table", table, "column", column};
    }

    private static String[] table(String database, String table)
    {
        return new String[] {"catalog", "internal", "database", database, "table", table};
    }

    @Test
    void grantsReadingOnWholeDatabasesTablesAndColumns() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .allow(List.of("bob"), List.of("select"), column("sales", "orders", "*"))
                .deny(List.of("bob"), List.of("select"), column("sales", "orders", "card"))
                .allow(List.of("@analysts"), List.of("select"), column("hr", "*", "*"))
                .deny(List.of("carol"), List.of("select"), column("hr", "salaries", "*")));

        assertThat(rows("alice", "SELECT count(*) FROM sales.orders")).isEqualTo("3");
        assertThat(role("grantforge_alice")).contains("internal.sales.*: Select_priv");
        node().run("CREATE TABLE sales.returns (id INT) DUPLICATE KEY(id) DISTRIBUTED BY HASH(id) BUCKETS 1 PROPERTIES ('replication_num' = '1')");
        try {
            assertThat(rows("alice", "SELECT count(*) FROM sales.returns")).as("a table created later is read at once").isEqualTo("0");
        }
        finally {
            node().run("DROP TABLE sales.returns");
        }
        assertThat(as("alice", "SELECT count(*) FROM hr.people").denied()).isTrue();

        assertThat(rows("bob", "SELECT id, region FROM sales.orders ORDER BY id")).startsWith("1\tEU");
        assertThat(as("bob", "SELECT card FROM sales.orders").denied()).isTrue();
        assertThat(as("bob", "SELECT * FROM sales.items").denied()).isTrue();

        assertThat(rows("carol", "SELECT name FROM hr.people")).as("through the Doris role analysts").isEqualTo("ann");
        assertThat(as("carol", "SELECT * FROM hr.salaries").denied()).isTrue();
        assertThat(role("grantforge_carol")).contains("internal.hr.people").doesNotContain("internal.hr.salaries", "internal.hr.*");
        assertThat(requireNonNull(agent().syncNow(), "outcome").changed()).as("nothing to do").isFalse();
    }

    @Test
    void grantsTableDatabaseAndCatalogPrivileges() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("alice"), List.of("create"), "catalog", "internal", "database", "team_*")
                .allow(List.of("alice"), List.of("load", "alter", "drop", "show_view"), table("team_*", "*"))
                .allow(List.of("alice"), List.of("select"), column("team_*", "*", "*"))
                .allow(List.of("alice"), List.of("show_view"), table("sales", "eu"))
                .allow(List.of("alice"), List.of("create_database"), "catalog", "internal")
                .allow(List.of("bob"), List.of("drop_database"), "catalog", "internal", "database", "hr"));
        assertThat(warned("GrantForge withholds creating databases in catalog internal for user alice")).isTrue();
        assertThat(warned("GrantForge withholds dropping database internal.hr for user bob")).isTrue();
        assertThat(as("alice", "CREATE DATABASE team_a").denied()).as("Doris could not give it without more").isTrue();
        node().run("CREATE DATABASE team_a");
        try {
            agent().syncNow();
            String create = "CREATE TABLE team_a.t (x INT) DUPLICATE KEY(x) DISTRIBUTED BY HASH(x) BUCKETS 1 PROPERTIES ('replication_num' = '1')";
            assertThat(rows("alice", create)).isEmpty();
            agent().syncNow();
            assertThat(rows("alice", "INSERT INTO team_a.t VALUES (1)")).isEmpty();
            assertThat(rows("alice", "SELECT x FROM team_a.t")).isEqualTo("1");
            assertThat(rows("alice", "ALTER TABLE team_a.t ADD COLUMN y INT DEFAULT '2'")).isEmpty();
            assertThat(rows("alice", "SHOW CREATE VIEW sales.eu")).contains("eu");
            assertThat(rows("alice", "DROP TABLE team_a.t")).isEmpty();
            assertThat(as("alice", "DROP DATABASE team_a").denied()).isTrue();
            assertThat(as("bob", "DROP DATABASE hr").denied()).isTrue();
        }
        finally {
            node().run("DROP DATABASE IF EXISTS team_a FORCE");
        }
    }

    @Test
    void rowFiltersBecomeRowPoliciesOfTheUsersRole() throws Exception
    {
        DorisSnapshots base = new DorisSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*"));
        publish(base.filter(List.of("alice"), "region = 'EU'", "sales", "orders"));
        assertThat(rows("alice", "SELECT count(*) FROM sales.orders")).isEqualTo("2");
        assertThat(rows("bob", "SELECT count(*) FROM sales.orders")).as("the filter is alice's").isEqualTo("3");
        assertThat(requireNonNull(agent().syncNow(), "outcome").rowPolicies()).as("the policy stays as it is").isZero();

        publish(new DorisSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*"))
                .filter(List.of("alice"), "region = 'US'", "sales", "orders"));
        assertThat(rows("alice", "SELECT id FROM sales.orders")).isEqualTo("2");
        assertThat(node().rows("SHOW ROW POLICY")).as("the hold is gone").noneMatch(row -> row.get(0).endsWith("_hold"));

        publish(new DorisSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*")));
        assertThat(rows("alice", "SELECT count(*) FROM sales.orders")).isEqualTo("3");
        assertThat(node().rows("SHOW ROW POLICY")).noneMatch(row -> row.get(0).startsWith("grantforge_"));
    }

    @Test
    void aRowFilterDorisRefusesShowsNoRows() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .filter(List.of("alice"), "no_such_column = 1", "sales", "orders"));
        assertThat(rows("alice", "SELECT count(*) FROM sales.orders")).isEqualTo("0");
        assertThat(warned("Doris refused the row filter of GrantForge policy")).isTrue();
    }

    @Test
    void grantsTheWorkloadGroupsPoliciesAllow() throws Exception
    {
        node().run("CREATE WORKLOAD GROUP IF NOT EXISTS etl_group PROPERTIES ('max_concurrency' = '10')");
        try {
            agent().syncNow();
            publish(new DorisSnapshots().allow(List.of("alice"), List.of("usage"), "workload_group", "etl_*")
                    .allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*")));
            assertThat(role("grantforge_alice")).contains("etl_group: Usage_priv");
            assertThat(node().as("alice", "alice-secret", "SET workload_group = 'etl_group'").ok()).isTrue();
            // A statement no earlier one ran, as Doris answers a cached result without its backends and so without the check.
            String query = "SELECT count(*) FROM sales.orders WHERE id > -" + System.nanoTime();
            DorisNode.Result bob = node().asSession("bob", "bob-secret", "SET workload_group = 'etl_group'", query);
            assertThat(bob.denied()).as("bob may not use the group: " + bob.output()).isTrue();
            DorisNode.Result alice = node().asSession("alice", "alice-secret", "SET workload_group = 'etl_group'", query);
            assertThat(alice.output()).as(alice.error()).isEqualTo("3");
        }
        finally {
            publish(new DorisSnapshots());
            node().run("DROP WORKLOAD GROUP IF EXISTS etl_group");
        }
    }

    @Test
    void policyAndServiceChangesTakeEffectAndRolesGo() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*")));
        assertThat(rows("alice", "SELECT count(*) FROM sales.orders")).isEqualTo("3");
        assertThat(role("grantforge_alice")).contains("GrantForge agent role of user alice");

        Reconciler.Outcome none = publish(new DorisSnapshots());
        assertThat(none.dropped()).isEqualTo(1);
        assertThat(as("alice", "SELECT count(*) FROM sales.orders").denied()).isTrue();

        publish(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*")).disabled());
        assertThat(role("grantforge_alice")).isEmpty();

        publish(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*")).serviceType("hive"));
        assertThat(role("grantforge_alice")).isEmpty();
        assertThat(warned("bound to a hive service")).isTrue();
    }

    @Test
    void undoesChangesMadeByHand() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .allow(List.of("bob"), List.of("select"), column("sales", "orders", "id")));
        node().run("GRANT SELECT_PRIV ON internal.hr.* TO ROLE 'grantforge_alice'", "REVOKE 'grantforge_alice' FROM 'alice'@'%'",
                "REVOKE SELECT_PRIV(id) ON internal.sales.orders FROM ROLE 'grantforge_bob'",
                "GRANT SELECT_PRIV(card) ON internal.sales.orders TO ROLE 'grantforge_bob'");
        assertThat(as("alice", "SELECT count(*) FROM sales.orders").denied()).isTrue();
        Reconciler.Outcome repaired = requireNonNull(agent().syncNow(), "outcome");
        assertThat(repaired.privileges()).isPositive();
        assertThat(rows("alice", "SELECT count(*) FROM sales.orders")).isEqualTo("3");
        assertThat(role("grantforge_alice")).doesNotContain("internal.hr");
        assertThat(rows("bob", "SELECT id FROM sales.orders ORDER BY id")).startsWith("1");
        assertThat(as("bob", "SELECT card FROM sales.orders").denied()).isTrue();
        assertThat(requireNonNull(agent().syncNow(), "outcome").changed()).as("nothing to do").isFalse();
    }

    @Test
    void leavesItselfSuperusersAndUnmanagedUsersAlone() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("@public"), List.of("select"), column("sales", "*", "*")));
        String roles = String.join(",", node().rows("SHOW ROLES").stream().map(row -> row.get(0)).toList());
        assertThat(roles.split(",")).contains("grantforge_alice", "grantforge_erin")
                .doesNotContain("grantforge_etl", "grantforge_admin", "grantforge_root", "grantforge_admin2", "grantforge_" + AGENT);
        assertThat(as("etl", "SELECT count(*) FROM sales.orders").denied()).isTrue();
        assertThat(warned("Doris user admin2 holds ADMIN_PRIV or NODE_PRIV, which no role binds")).isTrue();
    }

    @Test
    void grantsTheRoleToEveryIdentityOfAUser() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("erin"), List.of("select"), column("sales", "*", "*")));
        String users = role("grantforge_erin");
        assertThat(users).contains("'erin'@'%'").contains("'erin'@'10.%'");
    }

    @Test
    void reportsAccessesFromTheAuditLogAndGrantsOutsideGrantForge() throws Exception
    {
        DorisSnapshots policies = new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .allow(List.of("bob"), List.of("select"), column("sales", "orders", "*"));
        long denial = policies.nextId();
        policies.deny(List.of("bob"), List.of("select"), column("sales", "orders", "card"))
                .deny(List.of("dave"), List.of("select"), column("hr", "salaries", "*"));
        publish(policies);
        assertThat(warned("Doris user dave holds SELECT_PRIV ON internal.hr.salaries directly, outside GrantForge")).isTrue();
        agent().readLogNow();

        rows("alice", "SELECT region FROM sales.orders");
        assertThat(as("bob", "SELECT card FROM sales.orders").denied()).isTrue();
        rows("dave", "SELECT * FROM hr.salaries");
        eventually("the audit log is read", () -> agent().readLogNow() >= 0 && server().events().stream()
                .map(event -> event.path("user").asText()).distinct().filter(List.of("alice", "bob", "dave")::contains).count() == 3);

        JsonNode alice = event("alice");
        assertThat(alice.path("resource").asText()).isEqualTo("internal.sales.orders");
        assertThat(alice.path("accessType").asText()).isEqualTo("select");
        assertThat(alice.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(alice.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(alice.path("action").asText()).isEqualTo("SELECT");
        assertThat(alice.path("request").asText()).isEqualTo("SELECT region FROM sales.orders");
        assertThat(alice.path("clientIp").asText()).isNotEmpty().doesNotContain(":");

        JsonNode bob = event("bob");
        assertThat(bob.path("resource").asText()).as("Doris names the column or, in older releases, the table")
                .isIn("internal.sales.orders.card", "internal.sales.orders");
        assertThat(bob.path("outcome").asText()).isEqualTo("DENIED");
        assertThat(bob.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(bob.path("policyId").asText()).isEqualTo(Long.toString(denial));

        JsonNode dave = event("dave");
        assertThat(dave.path("resource").asText()).isEqualTo("internal.hr.salaries");
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
    void theShadedJarRunsAsAService() throws Exception
    {
        publish(new DorisSnapshots().allow(List.of("erin"), List.of("select"), column("sales", "*", "*")));
        Map<String, String> values = settings("jar", "gfjar_", "doris.user", AGENT, "doris.password.file", file("jar.password", AGENT_PASSWORD),
                "sync.interval.ms", "1000");
        StringBuilder properties = new StringBuilder();
        values.forEach((key, value) -> properties.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
        String settingsFile = file("jar.properties", properties.toString());
        Path jar = Path.of(requireNonNull(System.getProperty("grantforge.doris.agent.jar"), "grantforge.doris.agent.jar"));
        Path output = files.resolve("jar.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(), settingsFile)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            eventually("the jar keeps a role for erin", () -> {
                try {
                    return !role("gfjar_erin").isEmpty();
                }
                catch (SQLException failed) {
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
        assertThat(Files.readString(output)).contains("keeps Apache Doris " + version());
    }
}
