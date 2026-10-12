// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.apache.hive.jdbc.HiveDriver;
import org.devlive.grantforge.plugin.api.ConnectionResult;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Hive plugin against a real HiveServer2 of one release, with its embedded metastore: the connection test,
 * lookups of databases, tables, columns and permanent UDFs, and the mask expressions run by Hive itself.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class HivePluginContract
{
    private final HiveProvider provider = new HiveProvider();
    private @Nullable GenericContainer<?> hive;

    /**
     * The image under test.
     *
     * @return such as {@code apache/hive:4.0.1}
     */
    abstract String image();

    @BeforeAll
    void startServer() throws Exception
    {
        // The 3.1 image has no warehouse its user may write; /tmp works in every release.
        GenericContainer<?> server = new GenericContainer<>(DockerImageName.parse(image())).withEnv("SERVICE_NAME", "hiveserver2")
                .withEnv("SERVICE_OPTS", "-Dhive.metastore.warehouse.dir=/tmp/warehouse")
                .withExposedPorts(10000).waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
        hive = server;
        server.start();
        // HiveServer2 opens its port before its sessions work; wait until one does.
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        while (true) {
            try (Connection admin = admin(); Statement statement = admin.createStatement()) {
                statement.execute("SHOW DATABASES");
                break;
            }
            catch (SQLException notYet) {
                if (System.nanoTime() > deadline) {
                    throw notYet;
                }
                TimeUnit.SECONDS.sleep(2);
            }
        }
        try (Connection admin = admin(); Statement statement = admin.createStatement()) {
            for (String sql : List.of("CREATE DATABASE sales", "CREATE DATABASE sales_archive", "CREATE DATABASE hr",
                    "CREATE TABLE sales.orders (id INT, customer STRING, card STRING, placed DATE)",
                    "CREATE TABLE sales.order_items (order_id INT, sku STRING)",
                    "CREATE TABLE sales_archive.orders_2024 (id INT)",
                    "CREATE TABLE hr.salaries (employee STRING, amount DECIMAL(10, 2))",
                    "INSERT INTO sales.orders VALUES (1, 'Alice Smith 42', '4111111111111111', CAST('2024-07-15' AS DATE))",
                    "CREATE FUNCTION sales.my_upper AS 'org.apache.hadoop.hive.ql.udf.generic.GenericUDFUpper'")) {
                statement.execute(sql);
            }
        }
    }

    @AfterAll
    void stopServer()
    {
        GenericContainer<?> server = hive;
        if (server != null) {
            server.stop();
        }
    }

    private String url()
    {
        GenericContainer<?> server = requireNonNull(hive, "the server has not started");
        return "jdbc:hive2://" + server.getHost() + ":" + server.getMappedPort(10000) + "/default";
    }

    private Connection admin() throws SQLException
    {
        Properties properties = new Properties();
        properties.setProperty("user", "hive");
        return requireNonNull(new HiveDriver().connect(url(), properties), "connection");
    }

    private ServiceConfig service()
    {
        return new ServiceConfig("warehouse", Map.of(HiveProvider.URL, url(), HiveProvider.USER, "grantforge", HiveProvider.PASSWORD, "unused"));
    }

    private List<String> lookup(String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(service(), resource, typed, context, 50));
    }

    @Test
    void connects()
    {
        ConnectionResult result = provider.testConnection(service());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
    }

    @Test
    void looksUpDatabases()
    {
        assertThat(lookup("database", "sal", Map.of())).containsExactly("sales", "sales_archive");
        assertThat(lookup("database", "SALES_", Map.of())).as("without case; the underscore is no wildcard").containsExactly("sales_archive");
        assertThat(lookup("database", "", Map.of())).contains("default", "hr", "sales");
    }

    @Test
    void looksUpTablesWithinTheDatabasesOfThePolicy()
    {
        assertThat(lookup("table", "ord", Map.of("database", List.of("sales")))).containsExactly("order_items", "orders");
        assertThat(lookup("table", "ord", Map.of("database", List.of("sales*")))).containsExactly("order_items", "orders", "orders_2024");
        assertThat(lookup("table", "", Map.of("database", List.of("missing")))).isEmpty();
    }

    @Test
    void looksUpColumnsAndUdfs()
    {
        assertThat(lookup("column", "", Map.of("database", List.of("sales"), "table", List.of("orders")))).containsExactly("card", "customer",
                "id", "placed");
        assertThat(lookup("column", "c", Map.of("database", List.of("sales"), "table", List.of("order*")))).containsExactly("card", "customer");
        assertThat(lookup("udf", "my", Map.of("database", List.of("sales")))).contains("my_upper");
    }

    @Test
    void masksRunOnHive() throws SQLException
    {
        assertThat(mask("mask", "customer")).isEqualTo("Xxxxx Xxxxx nn");
        assertThat(mask("mask_show_last_4", "card")).isEqualTo("xxxxxxxxxxxx1111");
        assertThat(mask("mask_show_first_4", "card")).isEqualTo("4111xxxxxxxxxxxx");
        assertThat(mask("mask_hash", "customer")).hasSizeGreaterThan(30).isNotEqualTo("Alice Smith 42");
        assertThat(mask("mask_null", "customer")).isNull();
        assertThat(mask("mask_none", "customer")).isEqualTo("Alice Smith 42");
        assertThat(mask("mask_date_show_year", "placed")).isEqualTo("2024-01-01");
    }

    private @Nullable String mask(String name, String column) throws SQLException
    {
        DataMaskDefinition masks = requireNonNull(provider.definition().dataMask(), "masks");
        MaskTypeDefinition mask = masks.maskTypes().stream().filter(type -> type.name().equals(name)).findFirst().orElseThrow();
        String expression = requireNonNull(mask.transformer(), name).replace("{col}", column);
        try (Connection admin = admin(); Statement statement = admin.createStatement();
             ResultSet row = statement.executeQuery("SELECT " + expression + " FROM sales.orders")) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }
}
