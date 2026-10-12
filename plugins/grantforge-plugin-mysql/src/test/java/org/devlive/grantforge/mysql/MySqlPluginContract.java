// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.mariadb.jdbc.Driver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The MySQL plugin against a real server of one release: signing in with caching_sha2_password over TLS and without,
 * looking up what information_schema shows the lookup user, and the mask expressions run by the server itself.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
abstract class MySqlPluginContract
{
    private static final String ROOT_PASSWORD = "grantforge-root";

    private final MySqlProvider provider = new MySqlProvider();
    private @Nullable GenericContainer<?> mysql;

    /**
     * The image under test.
     *
     * @return such as {@code mysql:8.4}
     */
    abstract String image();

    @BeforeAll
    void startServer() throws SQLException
    {
        GenericContainer<?> server = new GenericContainer<>(DockerImageName.parse(image())).withExposedPorts(3306).withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
                // The entrypoint first runs a temporary server on no port; the real one says it listens on 3306.
                .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1).withStartupTimeout(Duration.ofMinutes(4)));
        mysql = server;
        server.start();
        try (Connection root = rootConnection(); Statement statement = root.createStatement()) {
            for (String sql : List.of(
                    "CREATE DATABASE sales", "CREATE DATABASE sales_archive", "CREATE DATABASE salesYarchive", "CREATE DATABASE Hr",
                    "CREATE TABLE sales.orders (id INT PRIMARY KEY, customer VARCHAR(64), card CHAR(16), placed DATE)",
                    "CREATE TABLE sales.order_items (order_id INT, sku VARCHAR(32))",
                    "CREATE TABLE sales.customers (id INT, name VARCHAR(64))",
                    "CREATE TABLE sales_archive.orders_2024 (id INT)",
                    "CREATE TABLE Hr.Salaries (Employee VARCHAR(64), Amount DECIMAL(10, 2))",
                    "INSERT INTO sales.orders VALUES (1, 'Alice Smith 42', '4111111111111111', '2024-07-15')",
                    "CREATE USER 'lookup'@'%' IDENTIFIED WITH caching_sha2_password BY 'lookup-secret'",
                    "GRANT SELECT ON sales.* TO 'lookup'@'%'")) {
                statement.execute(sql);
            }
        }
    }

    @AfterAll
    void stopServer()
    {
        GenericContainer<?> server = mysql;
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void connectsOverTlsAndChecksTheServer()
    {
        ConnectionResult result = provider.testConnection(lookupUser("sslMode=trust"));
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
    }

    @Test
    void connectsWithoutTlsWhenTheServersKeyMayBeFetched()
    {
        ConnectionResult result = provider.testConnection(lookupUser("sslMode=disable\nallowPublicKeyRetrieval=true"));
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
    }

    @Test
    void tellsAWrongPasswordFromAnUnreachableServer()
    {
        Map<String, String> values = new HashMap<>(lookupUser("sslMode=trust").values());
        values.put(MySqlProvider.PASSWORD, "wrong");
        ServiceConfig wrong = new ServiceConfig("sales", values);
        assertThat(provider.testConnection(wrong).message()).contains("Access denied");
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(wrong, "database", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class,
                        failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void looksUpTheDatabasesTheUserMaySee()
    {
        assertThat(lookup(lookupUser("sslMode=trust"), "database", "", Map.of())).contains("information_schema", "sales")
                .doesNotContain("sales_archive", "Hr", "mysql");
        ServiceConfig root = rootUser();
        assertThat(lookup(root, "database", "sales", Map.of())).containsExactlyInAnyOrder("sales", "sales_archive", "salesYarchive");
        assertThat(lookup(root, "database", "sales_", Map.of())).as("the underscore is no wildcard").containsExactly("sales_archive");
        assertThat(lookup(root, "database", "H", Map.of())).containsExactly("Hr");
    }

    @Test
    void looksUpTablesWithinTheDatabasesOfThePolicy()
    {
        ServiceConfig root = rootUser();
        assertThat(lookup(root, "table", "order", Map.of("database", List.of("sales")))).containsExactly("order_items", "orders");
        assertThat(lookup(root, "table", "order", Map.of("database", List.of("sales*")))).containsExactly("order_items", "orders", "orders_2024");
        assertThat(lookup(root, "table", "", Map.of("database", List.of("Hr")))).containsExactly("Salaries");
        assertThat(lookup(root, "table", "", Map.of("database", List.of("missing")))).isEmpty();
    }

    @Test
    void looksUpColumnsWithinTheTablesOfThePolicy()
    {
        ServiceConfig config = lookupUser("sslMode=trust");
        assertThat(lookup(config, "column", "", Map.of("database", List.of("sales"), "table", List.of("orders"))))
                .containsExactly("card", "customer", "id", "placed");
        assertThat(lookup(config, "column", "c", Map.of("database", List.of("sales"), "table", List.of("order*"))))
                .containsExactly("card", "customer");
        assertThat(lookup(config, "column", "", Map.of("database", List.of("Hr"), "table", List.of("Salaries"))))
                .as("information_schema hides what the user may not read").isEmpty();
        assertThat(lookup(rootUser(), "column", "", Map.of("database", List.of("Hr"), "table", List.of("Salaries"))))
                .containsExactly("Amount", "Employee");
    }

    @Test
    void masksRunOnTheServer() throws SQLException
    {
        assertThat(mask(transformer("mask"), "customer")).isEqualTo("Xxxxx Xxxxx nn");
        assertThat(mask(transformer("mask_show_last_4"), "card")).isEqualTo("xxxxxxxxxxxx1111");
        assertThat(mask(transformer("mask_show_first_4"), "card")).isEqualTo("4111xxxxxxxxxxxx");
        assertThat(mask(transformer("mask_hash"), "customer")).hasSize(64).isNotEqualTo("Alice Smith 42");
        assertThat(mask(transformer("mask_null"), "customer")).isNull();
        assertThat(mask(transformer("mask_none"), "customer")).isEqualTo("Alice Smith 42");
        assertThat(mask(transformer("mask_date_show_year"), "placed")).isEqualTo("2024-01-01");
        assertThat(mask(transformer("custom").replace("{expr}", "UPPER({col})"), "customer")).isEqualTo("ALICE SMITH 42");
    }

    private String transformer(String name)
    {
        DataMaskDefinition masks = requireNonNull(provider.definition().dataMask(), "masks");
        MaskTypeDefinition mask = masks.maskTypes().stream().filter(type -> type.name().equals(name)).findFirst().orElseThrow();
        return requireNonNull(mask.transformer(), name);
    }

    private @Nullable String mask(String transformer, String column) throws SQLException
    {
        try (Connection root = rootConnection(); Statement statement = root.createStatement();
             ResultSet row = statement.executeQuery("SELECT " + transformer.replace("{col}", column) + " FROM sales.orders")) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 50));
    }

    private ServiceConfig lookupUser(String options)
    {
        return new ServiceConfig("sales", Map.of(MySqlProvider.URL, "jdbc:mysql://" + address(),
                MySqlProvider.USER, "lookup", MySqlProvider.PASSWORD, "lookup-secret", MySqlProvider.PROPERTIES, options));
    }

    private ServiceConfig rootUser()
    {
        return new ServiceConfig("sales", Map.of(MySqlProvider.URL, "jdbc:mariadb://" + address()
                + "?sslMode=trust", MySqlProvider.USER, "root", MySqlProvider.PASSWORD, ROOT_PASSWORD));
    }

    private String address()
    {
        GenericContainer<?> server = requireNonNull(mysql, "the server has not started");
        return server.getHost() + ":" + server.getMappedPort(3306);
    }

    private Connection rootConnection() throws SQLException
    {
        Properties properties = new Properties();
        properties.setProperty("user", "root");
        properties.setProperty("password", ROOT_PASSWORD);
        properties.setProperty("sslMode", "trust");
        return new Driver().connect("jdbc:mariadb://" + address(), properties);
    }
}
