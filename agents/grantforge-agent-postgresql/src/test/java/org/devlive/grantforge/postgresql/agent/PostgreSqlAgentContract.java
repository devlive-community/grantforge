// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

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
 * The agent against a real PostgreSQL server of one release with pgaudit: privileges down to columns and on whole
 * tables, writing with serial columns, deleting, truncating, creating objects, schemas and temporary tables, roles as
 * groups, row filters as restrictive row security policies that leave others' rows alone, changes of policies and of
 * the service, changes made by hand undone, the users it leaves alone, names that need quoting, row filters PostgreSQL
 * refuses, the server log as access events with privileges outside GrantForge told apart and reported, sign-in with a
 * client certificate over TLS, and the shaded jar run as a service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class PostgreSqlAgentContract
{
    private static final String AGENT = "grantforge";
    private static final String AGENT_PASSWORD = "agent-secret";
    private static final String ODD = "o'brien \"x\"";
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
    private final Logger logger = Logger.getLogger("org.devlive.grantforge.postgresql.agent");
    private @Nullable PostgreSqlNode node;
    private @Nullable AgentServer server;
    private @Nullable PostgreSqlAgent agent;
    private long published = 1;

    /**
     * The release under test.
     *
     * @return such as {@code 17.11}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        PostgreSqlNode started = new PostgreSqlNode(version(), Files.createDirectories(files.resolve("tls")));
        node = started;
        started.run("postgres", "CREATE DATABASE sales", "CREATE DATABASE hr", "CREATE USER " + AGENT + " SUPERUSER PASSWORD '" + AGENT_PASSWORD + "'",
                "CREATE USER " + PostgreSqlNode.CERTIFICATE_USER + " SUPERUSER", "CREATE USER \"" + ODD.replace("\"", "\"\"") + "\" PASSWORD 'odd-secret'",
                "CREATE ROLE analysts", "CREATE ROLE readers", "GRANT readers TO analysts");
        for (String user : USERS) {
            started.run("postgres", "CREATE USER " + user + " PASSWORD '" + user + "-secret'");
        }
        started.run("postgres", "GRANT analysts TO carol");
        started.run("sales", "CREATE SCHEMA crm", "CREATE TABLE crm.orders (id serial PRIMARY KEY, region text, card text, amount numeric)",
                "INSERT INTO crm.orders (region, card, amount) VALUES ('EU', '4111', 10), ('US', '5500', 20), ('EU', '3400', 30)",
                "CREATE TABLE crm.items (order_id int, sku text)", "CREATE VIEW crm.regions AS SELECT DISTINCT region FROM crm.orders",
                "CREATE TABLE crm.\"odd-table\" (\"we ird\" int, \"it's\" int)", "INSERT INTO crm.\"odd-table\" VALUES (1, 2)",
                "CREATE TABLE crm.owned (id int, team text)", "INSERT INTO crm.owned VALUES (1, 'a'), (2, 'b')",
                "ALTER TABLE crm.owned ENABLE ROW LEVEL SECURITY", "CREATE POLICY team_a ON crm.owned FOR SELECT TO PUBLIC USING (team = 'a')");
        started.run("hr", "CREATE TABLE salaries (employee text, amount numeric)", "INSERT INTO salaries VALUES ('ann', 1000)",
                "CREATE TABLE people (name text)", "INSERT INTO people VALUES ('ann')", "GRANT SELECT ON salaries TO dave");
        logger.addHandler(capture);
        server = new AgentServer(new PostgreSqlSnapshots());
        agent = PostgreSqlAgent.start(AgentConfig.parse(settings("main", "grantforge_", node().host(), "postgresql.user", AGENT,
                "postgresql.password.file", file("agent.password", AGENT_PASSWORD)), "test"));
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

    private PostgreSqlNode node()
    {
        return requireNonNull(node, "node");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private PostgreSqlAgent agent()
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
    private Map<String, String> settings(String instance, String prefix, String hosts, String... more) throws IOException
    {
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + server().port(),
                "grantforge.token.file", file(instance + ".token", AgentServer.TOKEN), "grantforge.instance", "postgresql-" + instance,
                "grantforge.cache.dir", files.resolve("cache-" + instance).toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.postgresql.hosts", hosts, "grantforge.postgresql.role.prefix", prefix));
        values.putAll(Map.of("grantforge.postgresql.unmanaged.users", "etl", "grantforge.sync.interval.ms", "3600000",
                "grantforge.audit.server.log.interval.ms", "600000", "grantforge.audit.fold.window.ms", "0",
                "grantforge.postgresql.databases", "sales,hr"));
        for (int index = 0; index < more.length; index += 2) {
            values.put("grantforge." + more[index], more[index + 1]);
        }
        return values;
    }

    /** Publishes policies, waits until the agent has them and syncs. */
    private Reconciler.Outcome publish(PostgreSqlSnapshots policies) throws InterruptedException
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

    private PostgreSqlNode.Result as(String user, String database, String statement) throws IOException, InterruptedException
    {
        return node().as(user, ODD.equals(user) ? "odd-secret" : user + "-secret", database, statement);
    }

    private String rows(String user, String database, String statement) throws IOException, InterruptedException
    {
        PostgreSqlNode.Result result = as(user, database, statement);
        assertThat(result.ok()).as(user + ": " + statement + ": " + result.error()).isTrue();
        return result.output();
    }

    private boolean warned(String text)
    {
        return logged.stream().anyMatch(record -> String.valueOf(record.getMessage()).contains(text));
    }

    private static String[] column(String database, String schema, String table, String column)
    {
        return new String[] {"database", database, "schema", schema, "table", table, "column", column};
    }

    private String privileges(String database, String role) throws IOException, InterruptedException
    {
        return node().value(database, "SELECT coalesce(string_agg(table_schema || '.' || table_name || ':' || privilege_type, ',' ORDER BY 1), '')"
                + " FROM information_schema.role_table_grants WHERE grantee = '" + role + "'");
    }

    @Test
    void grantsReadingOnTablesAndColumnsAndThroughRoles() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*"))
                .allow(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "*"))
                .deny(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "card"))
                .allow(List.of("@readers"), List.of("select"), column("hr", "public", "*", "*"))
                .deny(List.of("carol"), List.of("select"), column("hr", "public", "salaries", "*")));

        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.orders")).isEqualTo("3");
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.regions")).as("views are tables to policies").isEqualTo("2");
        assertThat(privileges("sales", "grantforge_alice")).contains("crm.orders:SELECT", "crm.items:SELECT");
        node().run("sales", "CREATE TABLE crm.returns (id int)");
        try {
            assertThat(as("alice", "sales", "SELECT count(*) FROM crm.returns").denied()).as("a table created later waits for the next sync")
                    .isTrue();
            agent().syncNow();
            assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.returns")).isEqualTo("0");
        }
        finally {
            node().run("sales", "DROP TABLE crm.returns");
        }
        assertThat(as("alice", "hr", "SELECT count(*) FROM people").denied()).isTrue();

        assertThat(rows("bob", "sales", "SELECT id, region FROM crm.orders ORDER BY id")).startsWith("1|EU");
        assertThat(as("bob", "sales", "SELECT card FROM crm.orders").denied()).isTrue();
        assertThat(as("bob", "sales", "SELECT * FROM crm.items").denied()).isTrue();
        assertThat(privileges("sales", "grantforge_bob")).as("columns are granted one by one").doesNotContain("crm.orders:SELECT");

        assertThat(rows("carol", "hr", "SELECT name FROM people")).as("through the role analysts, a member of readers").isEqualTo("ann");
        assertThat(as("carol", "hr", "SELECT * FROM salaries").denied()).isTrue();
    }

    @Test
    void grantsWritingDeletingAndCreating() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select", "insert", "update"), column("sales", "crm", "orders", "*"))
                .deny(List.of("alice"), List.of("update"), column("sales", "crm", "orders", "card"))
                .allow(List.of("alice"), List.of("delete", "truncate"), "database", "sales", "schema", "crm", "table", "items")
                .allow(List.of("alice"), List.of("create"), "database", "sales", "schema", "crm")
                .allow(List.of("alice"), List.of("create_schema", "temporary"), "database", "sales"));
        try {
            assertThat(rows("alice", "sales", "INSERT INTO crm.orders (region, card, amount) VALUES ('APAC', '1', 5) RETURNING id"))
                    .as("the serial column's sequence comes with inserting").startsWith("4\n");
            assertThat(rows("alice", "sales", "UPDATE crm.orders SET amount = 6 WHERE id = 4")).isEqualTo("UPDATE 1");
            assertThat(as("alice", "sales", "UPDATE crm.orders SET card = '2' WHERE id = 4").denied()).isTrue();
            assertThat(as("alice", "sales", "DELETE FROM crm.orders WHERE id = 4").denied()).isTrue();
            assertThat(rows("alice", "sales", "DELETE FROM crm.items")).isEqualTo("DELETE 0");
            assertThat(rows("alice", "sales", "TRUNCATE crm.items")).isEqualTo("TRUNCATE TABLE");
            assertThat(rows("alice", "sales", "CREATE TABLE crm.mine (x int)")).isEqualTo("CREATE TABLE");
            assertThat(rows("alice", "sales", "CREATE SCHEMA team_a")).isEqualTo("CREATE SCHEMA");
            assertThat(rows("alice", "sales", "CREATE TEMP TABLE scratch (x int)")).isEqualTo("CREATE TABLE");
            assertThat(as("bob", "sales", "CREATE TABLE crm.theirs (x int)").denied()).isTrue();
            assertThat(as("bob", "sales", "CREATE TEMP TABLE scratch (x int)").ok()).as("PUBLIC may create temporary tables by default").isTrue();
        }
        finally {
            node().run("sales", "DELETE FROM crm.orders WHERE id > 3", "DROP TABLE IF EXISTS crm.mine", "DROP SCHEMA IF EXISTS team_a");
        }
    }

    @Test
    void rowFiltersBecomeRestrictiveRowSecurityPolicies() throws Exception
    {
        PostgreSqlSnapshots policies = new PostgreSqlSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*", "*"))
                .filter(List.of("alice"), "region = 'EU'", "sales", "crm", "orders")
                .filter(List.of("alice"), "id = 1", "sales", "crm", "owned");
        publish(policies);
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.orders")).isEqualTo("2");
        assertThat(rows("bob", "sales", "SELECT count(*) FROM crm.orders")).as("the filter is alice's").isEqualTo("3");
        assertThat(node().value("sales", "SELECT count(*) FROM crm.orders")).as("the owner sees every row").isEqualTo("3");
        assertThat(node().value("sales", "SELECT polname || ':' || polpermissive FROM pg_policy WHERE polrelid = 'crm.orders'::regclass"
                + " ORDER BY polname")).isEqualTo("grantforge:true\ngrantforge_alice:false");
        assertThat(rows("alice", "sales", "SELECT id FROM crm.owned")).as("the owner's own policy and alice's both hold").isEqualTo("1");
        assertThat(rows("bob", "sales", "SELECT id FROM crm.owned")).as("row security the owner turned on stays theirs").isEqualTo("1");
        assertThat(node().value("sales", "SELECT count(*) FROM pg_policy WHERE polrelid = 'crm.owned'::regclass AND polname = 'grantforge'"))
                .isEqualTo("0");
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.regions")).as("a view's rows come from its owner's reading").isEqualTo("2");

        publish(new PostgreSqlSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*", "*"))
                .filter(List.of("alice"), "region = 'US'", "sales", "crm", "orders"));
        assertThat(rows("alice", "sales", "SELECT id FROM crm.orders")).isEqualTo("2");

        publish(new PostgreSqlSnapshots().allow(List.of("alice", "bob"), List.of("select"), column("sales", "*", "*", "*"))
                .filter(List.of("alice"), "region = 'EU'", "sales", "crm", "regions"));
        assertThat(as("alice", "sales", "SELECT * FROM crm.regions").denied()).as("PostgreSQL cannot filter a view's rows").isTrue();
        assertThat(warned("whose rows PostgreSQL cannot filter")).isTrue();
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.orders")).isEqualTo("3");
        assertThat(node().value("sales", "SELECT relrowsecurity FROM pg_class WHERE oid = 'crm.orders'::regclass")).as("turned off again")
                .isEqualTo("f");
        assertThat(node().value("sales", "SELECT relrowsecurity FROM pg_class WHERE oid = 'crm.owned'::regclass")).isEqualTo("t");
        assertThat(node().value("sales", "SELECT string_agg(polname, ',') FROM pg_policy")).isEqualTo("team_a");
    }

    @Test
    void rowFiltersPostgreSqlRefusesShowNoRows() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*"))
                .filter(List.of("alice"), "no_such_column = 1", "sales", "crm", "orders")
                .filter(List.of("alice"), "true); DROP TABLE crm.items; --", "sales", "crm", "items"));
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.orders")).isEqualTo("0");
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.items")).as("a filter of two statements is refused").isEqualTo("0");
        assertThat(node().value("sales", "SELECT count(*) FROM pg_class WHERE oid = 'crm.items'::regclass")).isEqualTo("1");
        assertThat(warned("PostgreSQL refused the row filter")).isTrue();
        Reconciler.Outcome again = requireNonNull(agent().syncNow(), "outcome");
        assertThat(again.rowPolicies()).as("the replacements stay").isZero();
    }

    @Test
    void policyAndServiceChangesTakeEffectAndRolesGo() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*")));
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.orders")).isEqualTo("3");
        assertThat(node().value("postgres", "SELECT string_agg(rolname, ',') FROM pg_roles WHERE rolname LIKE 'grantforge\\_%'"))
                .isEqualTo("grantforge_alice");
        assertThat(node().value("postgres", "SELECT shobj_description(oid, 'pg_authid') FROM pg_roles WHERE rolname = 'grantforge_alice'"))
                .isEqualTo("GrantForge agent role of user alice");

        Reconciler.Outcome none = publish(new PostgreSqlSnapshots());
        assertThat(none.dropped()).isEqualTo(1);
        assertThat(as("alice", "sales", "SELECT count(*) FROM crm.orders").denied()).isTrue();

        publish(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*")).disabled());
        assertThat(node().value("postgres", "SELECT count(*) FROM pg_roles WHERE rolname LIKE 'grantforge\\_%'")).isEqualTo("0");

        publish(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*")).serviceType("hive"));
        assertThat(node().value("postgres", "SELECT count(*) FROM pg_roles WHERE rolname LIKE 'grantforge\\_%'")).isEqualTo("0");
        assertThat(warned("bound to a hive service")).isTrue();
    }

    @Test
    void undoesChangesMadeByHand() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*"))
                .filter(List.of("alice"), "region = 'EU'", "sales", "crm", "orders"));
        node().run("hr", "GRANT SELECT ON salaries TO grantforge_alice");
        node().run("postgres", "REVOKE grantforge_alice FROM alice");
        node().run("sales", "DROP POLICY grantforge_alice ON crm.orders", "ALTER TABLE crm.orders DISABLE ROW LEVEL SECURITY");
        assertThat(as("alice", "sales", "SELECT count(*) FROM crm.orders").denied()).isTrue();
        Reconciler.Outcome repaired = requireNonNull(agent().syncNow(), "outcome");
        assertThat(repaired.statements()).as("the privilege given by hand is revoked").isEqualTo(1);
        assertThat(rows("alice", "sales", "SELECT count(*) FROM crm.orders")).as("membership and filter are back").isEqualTo("2");
        assertThat(as("alice", "hr", "SELECT count(*) FROM salaries").denied()).isTrue();
        assertThat(requireNonNull(agent().syncNow(), "outcome").changed()).as("nothing to do").isFalse();
    }

    @Test
    void leavesItselfSuperusersAndUnmanagedUsersAlone() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("@public"), List.of("select"), column("sales", "*", "*", "*")));
        String roles = node().value("postgres", "SELECT string_agg(rolname, ',' ORDER BY rolname) FROM pg_roles WHERE rolname LIKE 'grantforge\\_%'");
        assertThat(roles.split(",")).contains("grantforge_alice", "grantforge_erin", "grantforge_" + ODD)
                .doesNotContain("grantforge_etl", "grantforge_postgres", "grantforge_" + AGENT, "grantforge_analysts");
        assertThat(as("etl", "sales", "SELECT count(*) FROM crm.orders").denied()).isTrue();
        assertThat(warned("PostgreSQL user postgres is a superuser")).isTrue();
    }

    @Test
    void namesThatNeedQuotingAreQuoted() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of(ODD), List.of("select"), column("sales", "crm", "odd-table", "we ird")));
        assertThat(rows(ODD, "sales", "SELECT \"we ird\" FROM crm.\"odd-table\"")).isEqualTo("1");
        assertThat(as(ODD, "sales", "SELECT \"it's\" FROM crm.\"odd-table\"").denied()).isTrue();
    }

    @Test
    void reportsAccessesFromTheServerLogAndPrivilegesOutsideGrantForge() throws Exception
    {
        PostgreSqlSnapshots policies = new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*"))
                .allow(List.of("alice"), List.of("truncate"), "database", "sales", "schema", "crm", "table", "items")
                .allow(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "*"));
        long denial = policies.nextId();
        policies.deny(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "card"))
                .deny(List.of("dave"), List.of("select"), column("hr", "public", "salaries", "*"));
        publish(policies);
        assertThat(warned("PostgreSQL user dave holds SELECT ON TABLE public.salaries, outside GrantForge")).isTrue();
        agent().readLogNow();

        rows("alice", "sales", "SELECT region FROM crm.orders");
        rows("alice", "sales", "TRUNCATE crm.items");
        assertThat(as("bob", "sales", "SELECT card FROM crm.orders").denied()).isTrue();
        rows("dave", "hr", "SELECT * FROM salaries");
        eventually("the server log is read", () -> agent().readLogNow() >= 0 && server().events().size() >= 4);

        JsonNode alice = event("alice", "select");
        assertThat(alice.path("resource").asText()).isEqualTo("sales.crm.orders");
        assertThat(alice.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(alice.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(alice.path("action").asText()).isEqualTo("SELECT");
        assertThat(alice.path("request").asText()).isEqualTo("SELECT region FROM crm.orders");
        assertThat(alice.path("clientIp").asText()).isEqualTo("127.0.0.1");
        assertThat(event("alice", "truncate").path("resource").asText()).isEqualTo("sales.crm.items");

        JsonNode bob = event("bob", "select");
        assertThat(bob.path("resource").asText()).as("PostgreSQL names the table").isEqualTo("sales.crm.orders");
        assertThat(bob.path("outcome").asText()).isEqualTo("DENIED");
        assertThat(bob.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(bob.path("policyId").asText()).isEqualTo(Long.toString(denial));

        JsonNode dave = event("dave", "select");
        assertThat(dave.path("resource").asText()).isEqualTo("hr.public.salaries");
        assertThat(dave.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(dave.path("enforcer").asText()).as("a privilege outside GrantForge allowed it").isEqualTo("NATIVE");
        assertThat(server().events()).noneMatch(event -> AGENT.equals(event.path("user").asText()));
    }

    private JsonNode event(String user, String type) throws InterruptedException
    {
        eventually("an event of " + user + " arrives", () -> server().events().stream().anyMatch(event -> user.equals(event.path("user").asText())
                && type.equals(event.path("accessType").asText())));
        return server().events().stream().filter(event -> user.equals(event.path("user").asText()) && type.equals(event.path("accessType").asText()))
                .reduce((first, last) -> last).orElseThrow();
    }

    @Test
    void signsInWithAClientCertificateOverTls() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("erin"), List.of("select"), column("sales", "*", "*", "*")));
        try (PostgreSqlAgent secure = PostgreSqlAgent.start(AgentConfig.parse(settings("tls", "gftls_", node().secureHost(), "postgresql.user",
                PostgreSqlNode.CERTIFICATE_USER, "postgresql.ssl.mode", "verify-full", "postgresql.tls.ca", node().caCertificate(), "postgresql.tls.cert",
                node().clientCertificate(), "postgresql.tls.key", node().clientKey(), "audit.server.log", "false"), "test"))) {
            eventually("the second agent takes the policies", () -> {
                Snapshot snapshot = secure.agent().snapshot();
                return snapshot != null && snapshot.policyVersion() == published;
            });
            secure.syncNow();
            assertThat(privileges("sales", "gftls_erin")).contains("crm.orders:SELECT");
        }
        finally {
            node().run("sales", "DROP OWNED BY gftls_erin");
            node().run("hr", "DROP OWNED BY gftls_erin");
            node().run("postgres", "DROP ROLE IF EXISTS gftls_erin");
        }
    }

    @Test
    void theShadedJarRunsAsAService() throws Exception
    {
        publish(new PostgreSqlSnapshots().allow(List.of("erin"), List.of("select"), column("sales", "*", "*", "*")));
        Map<String, String> values = settings("jar", "gfjar_", node().host(), "postgresql.user", AGENT, "postgresql.password.file",
                file("jar.password", AGENT_PASSWORD), "sync.interval.ms", "1000");
        StringBuilder properties = new StringBuilder();
        values.forEach((key, value) -> properties.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
        String settingsFile = file("jar.properties", properties.toString());
        Path jar = Path.of(requireNonNull(System.getProperty("grantforge.postgresql.agent.jar"), "grantforge.postgresql.agent.jar"));
        Path output = files.resolve("jar.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(), settingsFile)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            eventually("the jar keeps a role for erin", () -> {
                try {
                    return "1".equals(node().value("postgres", "SELECT count(*) FROM pg_roles WHERE rolname = 'gfjar_erin'"));
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
            node().run("sales", "DROP OWNED BY gfjar_erin");
            node().run("hr", "DROP OWNED BY gfjar_erin");
            node().run("postgres", "DROP ROLE IF EXISTS gfjar_erin");
        }
        assertThat(Files.readString(output)).contains("keeps PostgreSQL " + version());
    }
}
