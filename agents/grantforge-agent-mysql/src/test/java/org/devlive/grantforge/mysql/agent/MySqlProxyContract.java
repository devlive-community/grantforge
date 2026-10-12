// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.mysql.agent.proxy.Backend;
import org.devlive.grantforge.mysql.agent.proxy.Gate;
import org.devlive.grantforge.mysql.agent.proxy.ProxyServer;
import org.devlive.grantforge.mysql.agent.sql.Fixtures;
import org.devlive.grantforge.mysql.agent.sql.Policies;
import org.devlive.grantforge.mysql.agent.sql.TableName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.Driver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The proxy in front of a real MySQL server of one release, with MariaDB Connector/J and MySQL Connector/J as clients:
 * sign-in through the proxy, decisions down to columns, masks and row filters on reads and writes, prepared statements,
 * LOCAL INFILE, pipelined batches, packets over 16 MB, database and user changes, and TLS on both sides.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 6, unit = TimeUnit.MINUTES)
abstract class MySqlProxyContract
{
    private static final String ROOT_PASSWORD = "grantforge-root";
    private static final List<String> ALICE = List.of("alice");

    private final AtomicReference<Policies> policies = new AtomicReference<>();
    private final List<AccessEvent> events = new CopyOnWriteArrayList<>();
    private final List<AutoCloseable> closing = new ArrayList<>();
    private @Nullable GenericContainer<?> mysql;
    private @Nullable JdbcCatalog catalog;
    private @Nullable ExecutorService checks;
    private @Nullable ProxyServer plain;

    @TempDir
    Path files;

    /**
     * The image under test.
     *
     * @return such as {@code mysql:8.4}
     */
    abstract String image();

    @BeforeAll
    void start() throws Exception
    {
        GenericContainer<?> server = new GenericContainer<>(DockerImageName.parse(image())).withExposedPorts(3306)
                .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD).withCommand("--local-infile=1")
                .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1).withStartupTimeout(Duration.ofMinutes(4)));
        mysql = server;
        server.start();
        try (Connection root = root(); Statement statement = root.createStatement()) {
            for (String sql : List.of(
                    "CREATE DATABASE sales", "CREATE DATABASE hr",
                    "CREATE TABLE sales.orders (id INT PRIMARY KEY, customer_id INT, card VARCHAR(19), region VARCHAR(8), total DECIMAL(10, 2))",
                    "CREATE TABLE sales.items (order_id INT, sku VARCHAR(16), price DECIMAL(10, 2))",
                    "CREATE TABLE sales.customers (id INT PRIMARY KEY, name VARCHAR(64), email VARCHAR(64), region VARCHAR(8))",
                    "CREATE TABLE sales.notes (id INT PRIMARY KEY, body LONGTEXT)",
                    "CREATE TABLE hr.salaries (employee VARCHAR(64), Amount DECIMAL(10, 2))",
                    "INSERT INTO sales.orders VALUES (1, 1, '4111111111111111', 'EU', 10.00), (2, 2, '5500000000000004', 'US', 20.00),"
                            + " (3, 1, '340000000000009', 'EU', 30.00)",
                    "INSERT INTO sales.items VALUES (1, 'apple', 1.50), (2, 'pear', 2.50)",
                    "INSERT INTO sales.customers VALUES (1, 'Ann', 'ann@example.com', 'EU'), (2, 'Ben', 'ben@example.com', 'US')",
                    "INSERT INTO hr.salaries VALUES ('ann', 1000)",
                    "CREATE FUNCTION sales.discount(price DECIMAL(10, 2)) RETURNS DECIMAL(10, 2) DETERMINISTIC RETURN price * 0.9",
                    "CREATE USER 'alice'@'%' IDENTIFIED WITH caching_sha2_password BY 'alice-secret'",
                    "CREATE USER 'bob'@'%' IDENTIFIED WITH caching_sha2_password BY 'bob-secret'",
                    // MySQL lets both do everything; the proxy narrows it.
                    "GRANT ALL ON sales.* TO 'alice'@'%'", "GRANT ALL ON hr.* TO 'alice'@'%'", "GRANT ALL ON sales.* TO 'bob'@'%'",
                    "CREATE USER 'grantforge_meta'@'%' IDENTIFIED BY 'meta-secret'",
                    "GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'%'")) {
                statement.execute(sql);
            }
        }
        Properties metadata = new Properties();
        metadata.setProperty("user", "grantforge_meta");
        metadata.setProperty("password", "meta-secret");
        metadata.setProperty("sslMode", "trust");
        catalog = new JdbcCatalog("jdbc:mariadb://" + server.getHost() + ":" + server.getMappedPort(3306) + "/", metadata, Duration.ofSeconds(30),
                Clock.systemUTC());
        checks = Executors.newFixedThreadPool(4);
        plain = proxy(null, null, false);
    }

    @AfterAll
    void stop() throws Exception
    {
        for (AutoCloseable resource : closing) {
            resource.close();
        }
        if (checks != null) {
            checks.shutdownNow();
        }
        if (catalog != null) {
            catalog.close();
        }
        if (mysql != null) {
            mysql.stop();
        }
    }

    @BeforeEach
    void allowTheShop()
    {
        events.clear();
        use(shop());
    }

    private static Fixtures.Policy shop()
    {
        return new Fixtures.Policy().allow("sales", "*", "*", ALICE, "all").allow("sales", null, null, ALICE, "create", "drop")
                .allow("sales", "*", "*", List.of("bob"), "select");
    }

    private void use(Fixtures.Policy policy)
    {
        policies.set(policy.policies());
    }

    private ProxyServer proxy(@Nullable SslContext backendTls, @Nullable SslContext clientTls, boolean clientTlsRequired)
            throws InterruptedException
    {
        GenericContainer<?> server = requireNonNull(mysql, "mysql");
        Gate gate = new Gate(requireNonNull(catalog, "catalog"), policies::get, events::add, requireNonNull(checks, "checks"));
        ProxyServer proxy = ProxyServer.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                new Backend(server.getHost(), server.getMappedPort(3306), backendTls, clientTls, clientTlsRequired, 10_000), gate);
        closing.add(proxy);
        return proxy;
    }

    // ---- MariaDB Connector/J --------------------------------------------------------------------------------------

    private Connection mariadb(String user, String options) throws SQLException
    {
        ProxyServer proxy = requireNonNull(plain, "proxy");
        return mariadb(proxy, user, "sslMode=disable&allowPublicKeyRetrieval=true" + (options.isEmpty() ? "" : "&" + options));
    }

    private static Connection mariadb(ProxyServer proxy, String user, String options) throws SQLException
    {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", user + "-secret");
        Connection connection = new Driver().connect("jdbc:mariadb://127.0.0.1:" + proxy.address().getPort() + "/sales?" + options, properties);
        return requireNonNull(connection, "connection");
    }

    private static List<String> strings(Connection connection, String sql) throws SQLException
    {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }

    private static SQLException failure(Connection connection, String sql)
    {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        catch (SQLException failed) {
            return failed;
        }
        throw new AssertionError("not refused: " + sql);
    }

    @Test
    void signsInThroughTheProxyAndChecksColumns() throws SQLException
    {
        try (Connection alice = mariadb("alice", "")) {
            assertThat(strings(alice, "SELECT id FROM orders ORDER BY id")).containsExactly("1", "2", "3");
            SQLException denied = failure(alice, "SELECT employee FROM hr.salaries");
            assertThat(denied.getErrorCode()).isEqualTo(1143);
            assertThat(denied.getSQLState()).isEqualTo("42000");
            assertThat(denied.getMessage()).contains("for column 'employee' in table 'salaries' by GrantForge");
            assertThat(strings(alice, "SELECT 1")).as("the connection goes on").containsExactly("1");
        }
        assertThat(events).anySatisfy(event -> assertThat(EventFields.of(event)).containsEntry("resource", "sales.orders.id")
                .containsEntry("outcome", "ALLOWED").containsEntry("user", "alice").containsEntry("clientIp", "127.0.0.1"));
        assertThat(events).anySatisfy(event -> assertThat(EventFields.of(event)).containsEntry("resource", "hr.salaries.employee")
                .containsEntry("outcome", "DENIED"));
    }

    @Test
    void theServerStillChecksPasswords()
    {
        Properties properties = new Properties();
        properties.setProperty("user", "alice");
        properties.setProperty("password", "wrong");
        assertThatThrownBy(() -> new Driver().connect("jdbc:mariadb://127.0.0.1:" + requireNonNull(plain, "proxy").address().getPort()
                + "/sales?sslMode=disable&allowPublicKeyRetrieval=true", properties)).isInstanceOfSatisfying(SQLException.class,
                failed -> assertThat(failed.getMessage()).contains("Access denied for user 'alice'"));
    }

    @Test
    void masksColumnsAndFiltersRows() throws SQLException
    {
        use(shop().mask("sales", "orders", "card", "alice", "mask_show_last_4", null).filter("sales", "orders", "alice", "region = 'EU'"));
        try (Connection alice = mariadb("alice", "")) {
            assertThat(strings(alice, "SELECT card FROM orders ORDER BY id")).containsExactly("xxxxxxxxxxxx1111", "xxxxxxxxxxx0009");
            assertThat(strings(alice, "SELECT COUNT(*) FROM orders")).containsExactly("2");
            assertThat(strings(alice, "SELECT o.card FROM sales.orders o JOIN customers c ON c.id = o.customer_id WHERE c.name = 'Ann'"
                    + " AND o.card LIKE 'x%' ORDER BY o.id")).as("predicates see the masked values").hasSize(2);
            try (Statement statement = alice.createStatement()) {
                assertThat(statement.executeUpdate("UPDATE orders SET total = total + 1")).as("only EU rows change").isEqualTo(2);
            }
        }
        try (Connection root = root()) {
            assertThat(strings(root, "SELECT total FROM sales.orders ORDER BY id")).containsExactly("11.00", "20.00", "31.00");
            try (Statement statement = root.createStatement()) {
                statement.executeUpdate("UPDATE sales.orders SET total = total - 1 WHERE region = 'EU'");
            }
        }
    }

    @Test
    void preparedStatementsAreCheckedAndRepreparedWhenPoliciesChange() throws SQLException
    {
        use(shop().mask("sales", "orders", "card", "alice", "mask_null", null));
        try (Connection alice = mariadb("alice", "useServerPrepStmts=true");
             PreparedStatement select = alice.prepareStatement("SELECT card FROM orders WHERE id = ?")) {
            select.setInt(1, 2);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isNull();
            }
            use(shop());
            select.setInt(1, 2);
            assertThatThrownBy(select::executeQuery).isInstanceOfSatisfying(SQLException.class,
                    failed -> assertThat(failed.getErrorCode()).isEqualTo(1615));
            try (PreparedStatement salary = alice.prepareStatement("SELECT Amount FROM hr.salaries WHERE employee = ?")) {
                salary.setString(1, "ann");
                assertThatThrownBy(salary::executeQuery).isInstanceOfSatisfying(SQLException.class,
                        failed -> assertThat(failed.getErrorCode()).as(failed.toString()).isEqualTo(1143));
            }
            assertThat(strings(alice, "SELECT 1")).as("the connection goes on").containsExactly("1");
        }
    }

    @Test
    void followsTheDatabaseOnlyWhenTheServerChangesIt() throws SQLException
    {
        use(shop().allow("hr", "*", "*", ALICE, "select"));
        try (Connection alice = mariadb("alice", "")) {
            try (Statement statement = alice.createStatement()) {
                statement.execute("USE hr");
            }
            assertThat(strings(alice, "SELECT employee FROM salaries")).containsExactly("ann");
            // MySQL answers 1044 for a database the user has no grant on, 1049 for one that is missing.
            assertThat(failure(alice, "USE missing").getErrorCode()).isIn(1044, 1049);
            assertThat(strings(alice, "SELECT employee FROM salaries")).as("still in hr").containsExactly("ann");
            alice.setCatalog("sales");
            assertThat(strings(alice, "SELECT sku FROM items ORDER BY sku")).containsExactly("apple", "pear");
        }
    }

    @Test
    void refusesWhatItCannotSeeThroughAndKeepsTheOrderOfAnswers() throws SQLException
    {
        try (Connection alice = mariadb("alice", "allowMultiQueries=true")) {
            assertThat(failure(alice, "SELECT sales.discount(price) FROM items").getMessage()).contains("stored function");
            assertThat(failure(alice, "SELECT 1; SELECT employee FROM hr.salaries").getErrorCode()).isEqualTo(1143);
            assertThat(failure(alice, "SELECT /*!50000 employee, */ 1 FROM hr.salaries").getMessage()).contains("executable comments");
            try (Statement batch = alice.createStatement()) {
                batch.addBatch("INSERT INTO items VALUES (3, 'plum', 3.00)");
                batch.addBatch("INSERT INTO hr.salaries VALUES ('eve', 1)");
                batch.addBatch("INSERT INTO items VALUES (4, 'fig', 4.00)");
                assertThatThrownBy(batch::executeBatch).isInstanceOf(BatchUpdateException.class);
            }
            assertThat(strings(alice, "SELECT sku FROM items ORDER BY order_id")).startsWith("apple", "pear", "plum");
            try (Statement statement = alice.createStatement()) {
                statement.executeUpdate("DELETE FROM items WHERE order_id > 2");
            }
        }
        try (Connection root = root()) {
            assertThat(strings(root, "SELECT employee FROM hr.salaries")).containsExactly("ann");
        }
    }

    @Test
    void loadsLocalFilesItMayInsert() throws SQLException, IOException
    {
        Path csv = Files.writeString(files.resolve("items.csv"), "7,kiwi,7.00\n8,lime,8.00\n", StandardCharsets.UTF_8);
        try (Connection alice = mariadb("alice", "allowLocalInfile=true"); Statement statement = alice.createStatement()) {
            assertThat(statement.executeUpdate("LOAD DATA LOCAL INFILE '" + csv.toAbsolutePath() + "' INTO TABLE items FIELDS TERMINATED BY ','"))
                    .isEqualTo(2);
            assertThat(strings(alice, "SELECT sku FROM items WHERE order_id >= 7 ORDER BY order_id")).containsExactly("kiwi", "lime");
            statement.executeUpdate("DELETE FROM items WHERE order_id >= 7");
        }
    }

    @Test
    void relaysStatementsLargerThanOnePacket() throws SQLException
    {
        String body = "x".repeat(17 * 1024 * 1024);
        try (Connection alice = mariadb("alice", ""); Statement statement = alice.createStatement()) {
            statement.executeUpdate("INSERT INTO notes VALUES (1, '" + body + "')");
            assertThat(strings(alice, "SELECT LENGTH(body) FROM notes WHERE id = 1")).containsExactly(Integer.toString(body.length()));
            try (ResultSet row = statement.executeQuery("SELECT body FROM notes WHERE id = 1")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).hasSize(body.length());
            }
            statement.executeUpdate("DELETE FROM notes");
        }
    }

    @Test
    void refusesEverythingWithoutPoliciesAndNothingForAServiceNotInUse() throws SQLException
    {
        policies.set(Policies.of(null));
        try (Connection alice = mariadb("alice", "")) {
            assertThat(failure(alice, "SELECT id FROM orders").getErrorCode()).isEqualTo(1143);
            assertThat(strings(alice, "SELECT 1")).containsExactly("1");
        }
        use(new Fixtures.Policy().disabled());
        try (Connection bob = mariadb("bob", "")) {
            assertThat(strings(bob, "SELECT card FROM orders WHERE id = 1")).containsExactly("4111111111111111");
        }
    }

    // ---- MySQL Connector/J ----------------------------------------------------------------------------------------

    private Connection connectorJ(String user, String options) throws SQLException
    {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", user + "-secret");
        Connection connection = new com.mysql.cj.jdbc.Driver().connect("jdbc:mysql://127.0.0.1:" + requireNonNull(plain, "proxy").address().getPort()
                + "/sales?sslMode=DISABLED&allowPublicKeyRetrieval=true" + options, properties);
        return requireNonNull(connection, "connection");
    }

    @Test
    void servesMySqlsOwnDriver() throws SQLException
    {
        use(shop().mask("sales", "customers", "email", "alice", "mask_hash", null));
        try (Connection alice = connectorJ("alice", "&useServerPrepStmts=true")) {
            assertThat(strings(alice, "SELECT name FROM customers ORDER BY id")).containsExactly("Ann", "Ben");
            try (PreparedStatement email = alice.prepareStatement("SELECT email FROM customers WHERE id = ?")) {
                email.setInt(1, 1);
                try (ResultSet row = email.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).hasSize(64).doesNotContain("@");
                }
            }
            assertThat(failure(alice, "SELECT Amount FROM hr.salaries").getErrorCode()).isEqualTo(1143);
        }
    }

    @Test
    void followsAChangeOfUser() throws SQLException
    {
        try (Connection connection = connectorJ("alice", "")) {
            assertThat(strings(connection, "SELECT region FROM customers WHERE id = 1")).containsExactly("EU");
            connection.unwrap(com.mysql.cj.jdbc.JdbcConnection.class).changeUser("bob", "bob-secret");
            assertThat(strings(connection, "SELECT region FROM customers WHERE id = 1")).containsExactly("EU");
            assertThat(failure(connection, "DELETE FROM customers WHERE id = 9").getErrorCode()).as("bob may only select").isEqualTo(1142);
        }
        assertThat(events).anySatisfy(event -> assertThat(EventFields.of(event)).containsEntry("user", "bob").containsEntry("accessType", "delete")
                .containsEntry("outcome", "DENIED"));
    }

    // ---- TLS ------------------------------------------------------------------------------------------------------

    @Test
    // Netty's self-signed certificate is deprecated for production; it is all a test of TLS needs.
    @SuppressWarnings("deprecation")
    void speaksTlsOnBothSides() throws Exception
    {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        SslContext clientSide = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
        SslContext serverSide = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                .endpointIdentificationAlgorithm(null).build();
        ProxyServer secure = proxy(serverSide, clientSide, true);
        try (Connection alice = mariadb(secure, "alice", "sslMode=trust")) {
            assertThat(strings(alice, "SELECT sku FROM items ORDER BY sku")).containsExactly("apple", "pear");
            assertThat(strings(alice, "SHOW SESSION STATUS LIKE 'Ssl_cipher'")).hasSize(1);
        }
        try (Connection alice = mariadb(secure, "alice", "sslMode=trust&useServerPrepStmts=true");
             PreparedStatement select = alice.prepareStatement("SELECT total FROM orders WHERE id = ?")) {
            select.setInt(1, 1);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
            }
        }
        assertThatThrownBy(() -> mariadb(secure, "alice", "sslMode=disable&allowPublicKeyRetrieval=true"))
                .isInstanceOfSatisfying(SQLException.class, failed -> assertThat(failed.getMessage()).contains("must use TLS"));
        certificate.delete();
    }

    @Test
    @SuppressWarnings("deprecation")
    void carriesPasswordsAcrossSidesOfDifferentTls() throws Exception
    {
        SelfSignedCertificate certificate = new SelfSignedCertificate("localhost");
        SslContext clientSide = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
        SslContext serverSide = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                .endpointIdentificationAlgorithm(null).build();
        ProxyServer plainToTls = proxy(serverSide, null, false);
        ProxyServer tlsToPlain = proxy(null, clientSide, false);
        // Forgetting the cached passwords makes caching_sha2_password ask for the whole password again.
        flushPrivileges();
        try (Connection alice = mariadb(plainToTls, "alice", "sslMode=disable&allowPublicKeyRetrieval=true")) {
            assertThat(strings(alice, "SELECT sku FROM items ORDER BY sku")).containsExactly("apple", "pear");
        }
        flushPrivileges();
        try (Connection alice = mariadb(tlsToPlain, "alice", "sslMode=trust")) {
            assertThat(strings(alice, "SELECT sku FROM items ORDER BY sku")).containsExactly("apple", "pear");
        }
        flushPrivileges();
        Properties wrong = new Properties();
        wrong.setProperty("user", "alice");
        wrong.setProperty("password", "wrong");
        assertThatThrownBy(() -> new Driver().connect("jdbc:mariadb://127.0.0.1:" + plainToTls.address().getPort()
                + "/sales?sslMode=disable&allowPublicKeyRetrieval=true", wrong)).isInstanceOfSatisfying(SQLException.class,
                failed -> assertThat(failed.getMessage()).contains("Access denied"));
        certificate.delete();
    }

    private void flushPrivileges() throws SQLException
    {
        try (Connection root = root(); Statement statement = root.createStatement()) {
            statement.execute("FLUSH PRIVILEGES");
        }
    }

    private Connection root() throws SQLException
    {
        GenericContainer<?> server = requireNonNull(mysql, "mysql");
        Properties properties = new Properties();
        properties.setProperty("user", "root");
        properties.setProperty("password", ROOT_PASSWORD);
        properties.setProperty("sslMode", "trust");
        return requireNonNull(new Driver().connect("jdbc:mariadb://" + server.getHost() + ":" + server.getMappedPort(3306) + "/", properties),
                "root");
    }

    @Test
    void readsTheSchemaAsTheMetadataUser()
    {
        JdbcCatalog schema = requireNonNull(catalog, "catalog");
        assertThat(schema.columns(new TableName("sales", "orders"))).containsExactly("id", "customer_id",
                "card", "region", "total");
        assertThat(schema.columns(new TableName("sales", "missing"))).isNull();
        assertThat(schema.routine("sales", "DISCOUNT")).isTrue();
        assertThat(schema.routine("hr", "discount")).isFalse();
        assertThat(schema.foldsNames()).isFalse();
    }
}
