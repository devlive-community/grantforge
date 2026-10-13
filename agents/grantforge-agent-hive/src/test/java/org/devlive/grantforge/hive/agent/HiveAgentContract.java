// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shaded agent inside a real HiveServer2 of one release, with its embedded metastore, taking signed policies from a
 * GrantForge server: statements allowed and refused per user, SHOW lists filtered, masked columns and filtered rows,
 * writes refused where masks apply, Hive's grants refused, TRANSFORM disallowed, access events shipped, and policy
 * changes applied without a restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 15, unit = TimeUnit.MINUTES)
abstract class HiveAgentContract
{
    private static final List<String> HIVE = List.of("hive");
    private static final List<String> ALICE = List.of("alice");

    private @Nullable AgentServer server;
    private @Nullable GenericContainer<?> hive;
    private @Nullable URLClassLoader jdbc;
    private @Nullable Driver driver;

    /**
     * The image under test.
     *
     * @return such as {@code apache/hive:4.0.1}
     */
    abstract String image();

    private static HiveSnapshots policies()
    {
        return new HiveSnapshots()
                .allow(HIVE, List.of("all"), "database", "*", "table", "*", "column", "*")
                .allow(HIVE, List.of("all"), "database", "*", "udf", "*")
                .allow(HIVE, List.of("read", "write"), "url", "*")
                .allow(ALICE, List.of("select"), "database", "sales", "table", "orders", "column", "*")
                .allow(ALICE, List.of("select", "update"), "database", "sales", "table", "items", "column", "*")
                .mask(ALICE, "mask_hash", null, "database", "sales", "table", "orders", "column", "customer")
                .filter(ALICE, "region = 'EU'", "database", "sales", "table", "orders");
    }

    @BeforeAll
    void start() throws Exception
    {
        Path jar = Path.of(System.getProperty("grantforge.hive.agent.jar"));
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertThat(zip.getEntry("org/devlive/grantforge/hive/agent/GrantForgeHiveAuthorizerFactory.class")).isNotNull();
            assertThat(zip.getEntry("org/devlive/grantforge/hive/agent/internal/jackson/databind/ObjectMapper.class")).isNotNull();
            assertThat(zip.getEntry("org/apache/hadoop/hive/conf/HiveConf.class")).isNull();
            assertThat(zip.getEntry("com/fasterxml/jackson/databind/ObjectMapper.class")).isNull();
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {Path.of(System.getProperty("grantforge.hive.jdbc.jar")).toUri().toURL()},
                ClassLoader.getPlatformClassLoader());
        jdbc = loader;
        driver = (Driver) loader.loadClass("org.apache.hive.jdbc.HiveDriver").getConstructor().newInstance();
        AgentServer agents = new AgentServer(policies());
        server = agents;
        Testcontainers.exposeHostPorts(agents.port());
        String options = String.join(" ", "-Dhive.metastore.warehouse.dir=/tmp/warehouse",
                "-Dhive.security.authorization.enabled=true",
                "-Dhive.security.authorization.manager=" + GrantForgeHiveAuthorizerFactory.class.getName(),
                "-Dhive.security.authenticator.manager=org.apache.hadoop.hive.ql.security.SessionStateUserAuthenticator",
                "-Dhive.server2.enable.doAs=false",
                "-Dgrantforge.server.url=http://host.testcontainers.internal:" + agents.port(),
                "-Dgrantforge.token.file=/opt/grantforge/token", "-Dgrantforge.signing.key.file=/opt/grantforge/signing.key",
                "-Dgrantforge.instance=hs2-it", "-Dgrantforge.cache.dir=/tmp/grantforge-cache", "-Dgrantforge.refresh.interval.ms=1000",
                "-Dgrantforge.audit.batch.size=1", "-Dgrantforge.audit.flush.interval.ms=200");
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(image())).withEnv("SERVICE_NAME", "hiveserver2")
                .withEnv("SERVICE_OPTS", options).withExposedPorts(10000)
                .withCopyFileToContainer(MountableFile.forHostPath(jar), "/opt/hive/lib/grantforge-agent-hive.jar")
                .withCopyToContainer(Transferable.of(AgentServer.TOKEN.getBytes(StandardCharsets.UTF_8), 0644), "/opt/grantforge/token")
                .withCopyToContainer(Transferable.of(agents.publicKey().getBytes(StandardCharsets.US_ASCII), 0644), "/opt/grantforge/signing.key")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
        hive = container;
        container.start();
        // HiveServer2 opens its port before its sessions work; wait until one does.
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        while (true) {
            try (Connection admin = connect("hive"); Statement statement = admin.createStatement()) {
                statement.execute("SHOW DATABASES");
                break;
            }
            catch (SQLException notYet) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException(container.getLogs(), notYet);
                }
                TimeUnit.SECONDS.sleep(2);
            }
        }
        execute("hive", "CREATE DATABASE sales", "CREATE DATABASE hr",
                "CREATE TABLE sales.orders (id INT, customer STRING, region STRING)",
                "CREATE TABLE sales.items (order_id INT, sku STRING)", "CREATE TABLE sales.secrets (code STRING)",
                "CREATE TABLE hr.salaries (employee STRING, amount INT)",
                "INSERT INTO sales.orders VALUES (1, 'Ann', 'EU'), (2, 'Ben', 'US')", "INSERT INTO hr.salaries VALUES ('ann', 100)");
    }

    @AfterAll
    void stop() throws Exception
    {
        if (jdbc != null) {
            jdbc.close();
        }
        if (hive != null) {
            hive.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    private Connection connect(String user) throws SQLException
    {
        GenericContainer<?> container = requireNonNull(hive, "hive");
        Properties properties = new Properties();
        properties.setProperty("user", user);
        return requireNonNull(requireNonNull(driver, "driver").connect("jdbc:hive2://" + container.getHost() + ":" + container.getMappedPort(10000) + "/default",
                properties), "connection");
    }

    private void execute(String user, String... statements) throws SQLException
    {
        try (Connection connection = connect(user); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private List<List<String>> rows(String user, String sql) throws SQLException
    {
        List<List<String>> rows = new ArrayList<>();
        try (Connection connection = connect(user); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                List<String> row = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    row.add(String.valueOf(result.getString(column)));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String> first(String user, String sql) throws SQLException
    {
        return rows(user, sql).stream().map(row -> row.get(0)).toList();
    }

    @Test
    @Order(1)
    void queriesSeeMaskedColumnsAndFilteredRows() throws SQLException
    {
        List<List<String>> orders = rows("alice", "SELECT id, customer, region FROM sales.orders");
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).get(0)).isEqualTo("1");
        assertThat(orders.get(0).get(1)).isNotEqualTo("Ann").hasSizeGreaterThan(30);
        assertThat(orders.get(0).get(2)).isEqualTo("EU");
        assertThat(rows("hive", "SELECT id, customer FROM sales.orders ORDER BY id")).containsExactly(List.of("1", "Ann"), List.of("2", "Ben"));
    }

    @Test
    @Order(2)
    void refusesWhatThePoliciesDoNotAllow()
    {
        assertThatThrownBy(() -> rows("alice", "SELECT * FROM hr.salaries")).isInstanceOf(SQLException.class)
                .hasMessageContaining("Permission denied: user [alice] does not have [SELECT] privilege on [hr.salaries");
        assertThatThrownBy(() -> execute("alice", "DROP TABLE sales.items")).hasMessageContaining("does not have [DROP] privilege on [sales.items]");
        assertThatThrownBy(() -> execute("alice", "USE hr")).hasMessageContaining("does not have [USE] privilege on [hr]");
        assertThatThrownBy(() -> execute("bob", "CREATE DATABASE bobs")).hasMessageContaining("does not have [CREATE] privilege on [bobs]");
    }

    @Test
    @Order(3)
    void listsOnlyWhatTheUserMayUse() throws SQLException
    {
        assertThat(first("alice", "SHOW DATABASES")).containsExactly("sales");
        assertThat(first("alice", "SHOW TABLES IN sales")).containsExactlyInAnyOrder("items", "orders");
        assertThat(first("hive", "SHOW TABLES IN sales")).containsExactlyInAnyOrder("items", "orders", "secrets");
        assertThat(first("bob", "SHOW DATABASES")).isEmpty();
    }

    @Test
    @Order(4)
    void writesNeedUpdateAndNoMasks() throws SQLException
    {
        execute("alice", "INSERT INTO sales.items VALUES (1, 'apple')");
        assertThat(first("hive", "SELECT sku FROM sales.items")).containsExactly("apple");
        // Hive 4.1 names the columns an INSERT writes, earlier releases the table.
        assertThatThrownBy(() -> execute("alice", "INSERT INTO sales.orders VALUES (3, 'Cy', 'EU')"))
                .hasMessageContaining("does not have [UPDATE] privilege on [sales.orders");
    }

    @Test
    @Order(5)
    void hiveGrantsDfsAndTransformScriptsAreRefused()
    {
        assertThatThrownBy(() -> execute("hive", "GRANT SELECT ON TABLE sales.orders TO USER bob")).hasMessageContaining("managed in GrantForge");
        assertThatThrownBy(() -> execute("hive", "CREATE ROLE analysts")).hasMessageContaining("managed in GrantForge");
        assertThatThrownBy(() -> execute("alice", "SELECT TRANSFORM(order_id) USING 'cat' AS (x) FROM sales.items"))
                .hasMessageContaining("transform");
    }

    @Test
    @Order(6)
    void shipsAccessEvents() throws Exception
    {
        AgentServer agents = requireNonNull(server, "server");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!denied(agents.events(), "alice", "hr.salaries") && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }
        assertThat(denied(agents.events(), "alice", "hr.salaries")).isTrue();
        assertThat(agents.events()).anySatisfy(event -> {
            assertThat(event.path("user").asText()).isEqualTo("alice");
            assertThat(event.path("resource").asText()).isEqualTo("sales.orders.customer");
            assertThat(event.path("outcome").asText()).isEqualTo("ALLOWED");
            assertThat(event.path("action").asText()).isEqualTo("QUERY");
            assertThat(event.path("request").asText()).contains("FROM sales.orders");
        });
    }

    private static boolean denied(List<JsonNode> events, String user, String resource)
    {
        return events.stream().anyMatch(event -> user.equals(event.path("user").asText()) && event.path("resource").asText().startsWith(resource)
                && "DENIED".equals(event.path("outcome").asText()));
    }

    @Test
    @Order(7)
    void appliesPolicyChangesWithoutARestart() throws Exception
    {
        AgentServer agents = requireNonNull(server, "server");
        agents.publish(policies().allow(ALICE, List.of("select"), "database", "hr", "table", "salaries", "column", "*"));
        assertThat(eventually(() -> rows("alice", "SELECT employee, amount FROM hr.salaries"))).containsExactly(List.of("ann", "100"));
        agents.publish(policies().disabled());
        assertThat(eventually(() -> rows("bob", "SELECT employee FROM hr.salaries"))).as("a service not in use leaves Hive unchecked")
                .containsExactly(List.of("ann"));
        agents.publish(policies());
        assertThat(eventually(() -> {
            try {
                rows("bob", "SELECT employee FROM hr.salaries");
                throw new SQLException("still allowed");
            }
            catch (SQLException refused) {
                if (!String.valueOf(refused.getMessage()).contains("Permission denied")) {
                    throw refused;
                }
                return List.of();
            }
        })).isEmpty();
    }

    /** What a query returns once the agent applied the policies it needs, within a minute. */
    private static <T> T eventually(Query<T> query) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        while (true) {
            try {
                return query.run();
            }
            catch (SQLException notYet) {
                if (System.nanoTime() > deadline) {
                    throw notYet;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
    }

    /** A query that fails until the policies it needs apply. */
    @FunctionalInterface
    private interface Query<T>
    {
        T run() throws SQLException;
    }
}
