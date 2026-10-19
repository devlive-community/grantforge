// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import com.fasterxml.jackson.databind.JsonNode;
import io.trino.jdbc.TrinoDriver;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shaded agent jar in a real coordinator of one Trino release, against a GrantForge server of the tests' own: what
 * a user reads and writes is checked, listings keep what the user may use, rows are filtered and columns masked, Trino's
 * own grants are refused, other users' queries are seen under policy, and a policy change applies without a restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class TrinoAgentContract
{
    private @Nullable AgentServer server;
    private @Nullable GenericContainer<?> trino;

    /**
     * The release under test.
     *
     * @return such as {@code 476}
     */
    abstract String version();

    /**
     * The shaded agent jar the build made.
     *
     * @return its path
     */
    Path jar()
    {
        return Path.of(requireNonNull(System.getProperty("grantforge.trino.agent.jar"), "grantforge.trino.agent.jar"));
    }

    private static TrinoSnapshots policies()
    {
        return new TrinoSnapshots()
                .allow(List.of("admin"), List.of("all"), "catalog", "memory")
                .allow(List.of("admin"), List.of("create_function", "drop_function", "execute"), "catalog", "memory", "schema", "*", "function", "*")
                .allow(List.of("alice"), List.of("execute"), "catalog", "memory", "schema", "sales", "function", "twice")
                .allow(List.of("@analysts"), List.of("select"), "catalog", "memory", "schema", "sales")
                .allow(List.of("alice"), List.of("insert"), "catalog", "memory", "schema", "sales", "table", "returns")
                .deny(List.of("alice"), List.of("select"), "catalog", "memory", "schema", "sales", "table", "orders", "column", "card")
                .filter(List.of("alice"), "region = 'EU'", "catalog", "memory", "schema", "sales", "table", "orders")
                .mask(List.of("alice"), "mask_hash", null, "catalog", "memory", "schema", "sales", "table", "orders", "column", "customer")
                .allow(List.of("alice"), List.of("select"), "catalog", "system", "schema", "runtime")
                .group("analysts", "alice");
    }

    @BeforeAll
    @SuppressWarnings("resource")
    void start() throws Exception
    {
        AgentServer started = new AgentServer(policies());
        server = started;
        Testcontainers.exposeHostPorts(started.port());
        String settings = String.join("\n", "access-control.name=grantforge", "grantforge.server.url=http://host.testcontainers.internal:"
                + started.port() + "/", "grantforge.token.file=/etc/trino/grantforge.token", "grantforge.instance=coordinator",
                "grantforge.cache.dir=/tmp/grantforge", "grantforge.refresh.interval.ms=1000", "grantforge.audit.flush.interval.ms=200",
                "grantforge.audit.fold.window.ms=0") + "\n";
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("trinodb/trino:" + version())).withExposedPorts(8080)
                .withCopyFileToContainer(MountableFile.forHostPath(jar()), "/usr/lib/trino/plugin/grantforge/grantforge-agent.jar")
                .withCopyToContainer(Transferable.of(settings), "/etc/trino/access-control.properties")
                .withCopyToContainer(Transferable.of(AgentServer.TOKEN.getBytes(StandardCharsets.UTF_8), 0644), "/etc/trino/grantforge.token")
                .waitingFor(Wait.forLogMessage(".*======== SERVER STARTED ========.*", 1).withStartupTimeout(Duration.ofMinutes(4)));
        trino = container;
        container.start();
        eventually("the agent takes the policies", () -> succeeds("admin", "SELECT 1 FROM memory.information_schema.schemata"));
        for (String sql : List.of("CREATE SCHEMA memory.sales", "CREATE SCHEMA memory.hr",
                "CREATE TABLE memory.sales.orders AS SELECT id, CAST(customer AS varchar) AS customer, region, card FROM (VALUES (1, 'ann', 'EU',"
                        + " '4111'), (2, 'ben', 'US', '5500'), (3, 'cy', 'EU', '3400')) AS t (id, customer, region, card)",
                "CREATE TABLE memory.sales.returns (id integer)", "CREATE TABLE memory.hr.salaries (employee varchar, salary bigint)",
                "CREATE FUNCTION memory.sales.twice(x bigint) RETURNS bigint RETURN x * 2",
                "CREATE FUNCTION memory.sales.thrice(x bigint) RETURNS bigint RETURN x * 3")) {
            execute("admin", sql);
        }
    }

    @AfterAll
    void stop()
    {
        if (trino != null) {
            trino.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    private Connection connect(String user) throws SQLException
    {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        GenericContainer<?> container = requireNonNull(trino, "trino");
        return new TrinoDriver().connect("jdbc:trino://localhost:" + container.getMappedPort(8080), properties);
    }

    private void execute(String user, String sql) throws SQLException
    {
        try (Connection connection = connect(user); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<List<String>> query(String user, String sql) throws SQLException
    {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = connect(user); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    row.add(String.valueOf(result.getObject(column)));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String> column(String user, String sql) throws SQLException
    {
        return query(user, sql).stream().map(row -> row.get(0)).toList();
    }

    private boolean succeeds(String user, String sql)
    {
        try {
            query(user, sql);
            return true;
        }
        catch (SQLException refused) {
            return false;
        }
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(250);
        }
    }

    @Test
    void readsWhatThePoliciesAllowFilteredAndMasked() throws SQLException
    {
        assertThat(query("alice", "SELECT id, region FROM memory.sales.orders ORDER BY id")).as("only the rows of the filter")
                .containsExactly(List.of("1", "EU"), List.of("3", "EU"));
        assertThat(column("alice", "SELECT customer FROM memory.sales.orders ORDER BY id")).hasSize(2)
                .allSatisfy(customer -> assertThat(customer).hasSize(64).matches("[0-9A-F]+"));
        assertThat(column("admin", "SELECT customer FROM memory.sales.orders ORDER BY id")).containsExactly("ann", "ben", "cy");
    }

    @Test
    void refusesWhatThePoliciesDoNotAllow()
    {
        assertThatThrownBy(() -> query("alice", "SELECT card FROM memory.sales.orders")).isInstanceOf(SQLException.class)
                .hasMessageContaining("Access Denied: alice may not select memory.sales.orders.card: GrantForge policy 6 denies it");
        assertThatThrownBy(() -> query("alice", "SELECT * FROM memory.hr.salaries"))
                .hasMessageContaining("alice may not select memory.hr.salaries.employee: no GrantForge policy allows it");
        assertThatThrownBy(() -> query("bob", "SELECT id FROM memory.sales.orders")).as("Trino words a catalog the user may not use")
                .hasMessageContaining("Access Denied: Cannot access catalog memory");
        assertThatThrownBy(() -> execute("alice", "DROP TABLE memory.sales.orders")).hasMessageContaining("alice may not drop memory.sales.orders");
    }

    @Test
    void writesWhatThePoliciesAllow() throws SQLException
    {
        execute("alice", "INSERT INTO memory.sales.returns VALUES (1)");
        assertThatThrownBy(() -> execute("alice", "INSERT INTO memory.hr.salaries VALUES ('alice', 1)"))
                .hasMessageContaining("alice may not insert memory.hr.salaries");
        assertThatThrownBy(() -> execute("alice", "INSERT INTO memory.sales.orders VALUES (4, 'dee', 'EU', '1234')"))
                .hasMessageContaining("alice may not insert memory.sales.orders: no GrantForge policy allows it");
    }

    @Test
    void listsOnlyWhatAUserMayUse() throws SQLException
    {
        assertThat(column("alice", "SHOW CATALOGS")).contains("memory", "system").doesNotContain("tpch", "jmx");
        assertThat(column("alice", "SHOW SCHEMAS FROM memory")).contains("sales", "information_schema").doesNotContain("hr", "default");
        assertThat(column("alice", "SHOW TABLES FROM memory.sales")).containsExactlyInAnyOrder("orders", "returns");
        assertThat(column("alice", "SELECT column_name FROM memory.information_schema.columns WHERE table_name = 'orders' ORDER BY 1"))
                .containsExactly("customer", "id", "region");
        assertThat(column("alice", "SELECT table_name FROM system.jdbc.tables WHERE table_schem = 'sales' ORDER BY 1"))
                .as("what drivers read stays open, filtered").containsExactly("orders", "returns");
        assertThatThrownBy(() -> query("alice", "SHOW TABLES FROM memory.hr")).hasMessageContaining("alice may not show tables in memory.hr");
        assertThatThrownBy(() -> query("bob", "SHOW SCHEMAS FROM memory")).hasMessageContaining("Cannot access catalog memory");
    }

    @Test
    void runsAndListsTheFunctionsAUserMayRun() throws SQLException
    {
        assertThat(column("alice", "SELECT memory.sales.twice(21)")).containsExactly("42");
        assertThatThrownBy(() -> query("alice", "SELECT memory.sales.thrice(1)")).hasMessageContaining("thrice");
        List<String> functions = column("alice", "SHOW FUNCTIONS FROM memory.sales");
        assertThat(functions).contains("twice").doesNotContain("thrice");
        assertThat(column("alice", "SELECT abs(-1)")).as("Trino's own functions").containsExactly("1");
    }

    @Test
    void refusesTrinosOwnGrants()
    {
        assertThatThrownBy(() -> execute("admin", "GRANT SELECT ON memory.sales.orders TO USER bob"))
                .hasMessageContaining("GrantForge manages privileges and roles");
    }

    @Test
    void showsOtherUsersQueriesUnderPolicy() throws SQLException
    {
        query("admin", "SELECT count(*) FROM memory.sales.orders");
        List<String> users = column("alice", "SELECT DISTINCT \"user\" FROM system.runtime.queries");
        assertThat(users).contains("alice").doesNotContain("admin");
    }

    @Test
    void appliesAPolicyChangeWithoutARestart() throws Exception
    {
        AgentServer running = requireNonNull(server, "server");
        assertThat(succeeds("carol", "SELECT id FROM memory.sales.returns")).isFalse();
        running.publish(policies().allow(List.of("carol"), List.of("select"), "catalog", "memory", "schema", "sales", "table", "returns"));
        eventually("carol may read the returns", () -> succeeds("carol", "SELECT id FROM memory.sales.returns"));
        running.publish(policies());
        eventually("carol may no longer", () -> !succeeds("carol", "SELECT id FROM memory.sales.returns"));
    }

    private static boolean salaries(JsonNode event)
    {
        return "memory.hr.salaries".equals(event.path("resource").asText()) && "DENIED".equals(event.path("outcome").asText());
    }

    @Test
    void reportsAccessesToTheServer() throws Exception
    {
        assertThat(succeeds("alice", "SELECT employee FROM memory.hr.salaries")).isFalse();
        AgentServer running = requireNonNull(server, "server");
        eventually("the refusal arrives", () -> running.events().stream().anyMatch(TrinoAgentContract::salaries));
        JsonNode refused = running.events().stream().filter(TrinoAgentContract::salaries).findFirst().orElseThrow();
        assertThat(refused.path("outcome").asText()).isEqualTo("DENIED");
        assertThat(refused.path("user").asText()).isEqualTo("alice");
        assertThat(refused.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(refused.path("request").asText()).startsWith("query ");
    }
}
