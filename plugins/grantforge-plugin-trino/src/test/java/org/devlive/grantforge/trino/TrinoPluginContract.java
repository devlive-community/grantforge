// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import io.trino.jdbc.TrinoDriver;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Trino plugin against a real coordinator of one Trino release: the connection test, lookups of catalogs, schemas,
 * tables, columns and session properties narrowed by what a policy names above them, and a coordinator that cannot be
 * reached.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class TrinoPluginContract
{
    private final TrinoProvider provider = new TrinoProvider();
    private @Nullable TrinoServer server;

    /**
     * The release under test.
     *
     * @return such as {@code 476}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        TrinoServer started = new TrinoServer(version());
        server = started;
        started.start();
        Properties properties = new Properties();
        properties.setProperty("user", "admin");
        try (Connection connection = new TrinoDriver().connect(started.url(), properties); Statement statement = connection.createStatement()) {
            for (String sql : List.of("CREATE SCHEMA memory.sales", "CREATE SCHEMA memory.hr",
                    "CREATE TABLE memory.sales.orders (id bigint, customer varchar, region varchar)",
                    "CREATE TABLE memory.sales.order_items (order_id bigint, sku varchar)", "CREATE TABLE memory.hr.salaries (employee varchar)")) {
                statement.execute(sql);
            }
        }
    }

    @AfterAll
    void stop()
    {
        if (server != null) {
            server.stop();
        }
    }

    private ServiceConfig service(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(TrinoSettings.URL, requireNonNull(server, "server").url(), TrinoSettings.USER,
                "grantforge", TrinoSettings.TIMEOUT, "15000"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("warehouse", map);
    }

    private List<String> lookup(String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(service(), resource, typed, context, 50));
    }

    @Test
    void connectsAndDescribesTheCoordinator()
    {
        ConnectionResult result = provider.testConnection(service());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).startsWith("Trino " + version()).endsWith(" catalogs");
    }

    @Test
    void looksUpCatalogsSchemasTablesAndColumns()
    {
        assertThat(lookup("catalog", "", Map.of())).contains("jmx", "memory", "system", "tpcds", "tpch");
        assertThat(lookup("catalog", "tp", Map.of())).containsExactly("tpcds", "tpch");
        assertThat(lookup("schema", "", Map.of("catalog", List.of("memory")))).contains("default", "hr", "sales");
        assertThat(lookup("schema", "s", Map.of("catalog", List.of("mem*")))).containsExactly("sales");
        assertThat(lookup("table", "", Map.of("catalog", List.of("memory"), "schema", List.of("sales")))).containsExactly("order_items", "orders");
        assertThat(lookup("table", "order_", Map.of("catalog", List.of("memory"), "schema", List.of("*")))).as("an underscore is no wildcard")
                .containsExactly("order_items");
        assertThat(lookup("table", "", Map.of("catalog", List.of("memory"), "schema", List.of("h?")))).containsExactly("salaries");
        assertThat(lookup("column", "", Map.of("catalog", List.of("memory"), "schema", List.of("sales"), "table", List.of("orders"))))
                .containsExactly("customer", "id", "region");
        assertThat(lookup("system_information", "", Map.of())).containsExactly("*");
    }

    @Test
    void looksUpSessionProperties()
    {
        assertThat(lookup("system_session_property", "query_max", Map.of())).contains("query_max_run_time");
        assertThat(lookup("system_session_property", "", Map.of())).noneMatch(name -> name.contains("."));
        assertThat(lookup("session_property", "", Map.of("catalog", List.of("nothing")))).isEmpty();
    }

    @Test
    void namesACoordinatorThatCannotBeReached() throws Exception
    {
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        ServiceConfig config = service(TrinoSettings.URL, "jdbc:trino://localhost:" + closed, TrinoSettings.TIMEOUT, "3000");
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config, "catalog", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).as(String.valueOf(failed.getMessage())).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(provider.testConnection(config).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }
}
