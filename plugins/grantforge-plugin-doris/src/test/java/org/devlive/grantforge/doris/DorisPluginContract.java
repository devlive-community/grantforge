// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Doris plugin against a real cluster of one Doris release: the connection test, lookups of catalogs, databases,
 * tables, columns and workload groups within what a policy names, what a lookup user with few privileges sees, going
 * to the next frontend when one is down, and refused sign-ins.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 15, unit = TimeUnit.MINUTES)
abstract class DorisPluginContract
{
    private final DorisProvider provider = new DorisProvider();
    private @Nullable DorisNode node;

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
        String table = " DISTRIBUTED BY HASH(%s) BUCKETS 1 PROPERTIES ('replication_num' = '1')";
        started.run("CREATE DATABASE sales", "CREATE DATABASE hr", "CREATE DATABASE sales_2026",
                "CREATE TABLE sales.orders (id INT, Region VARCHAR(8), amount DOUBLE)" + table.formatted("id"),
                "CREATE TABLE sales.order_items (order_id INT, sku VARCHAR(8))" + table.formatted("order_id"),
                "CREATE TABLE hr.salaries (employee VARCHAR(8), amount DOUBLE)" + table.formatted("employee"),
                "CREATE TABLE sales_2026.orders (id INT, total DOUBLE)" + table.formatted("id"),
                "CREATE WORKLOAD GROUP IF NOT EXISTS etl PROPERTIES ('max_concurrency' = '10')",
                "CREATE USER 'lookup'@'%' IDENTIFIED BY 'lookup-secret'", "GRANT ADMIN_PRIV ON *.*.* TO 'lookup'@'%'",
                "CREATE USER 'reader'@'%' IDENTIFIED BY 'reader-secret'", "GRANT SELECT_PRIV(id) ON internal.sales.orders TO 'reader'@'%'");
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
    }

    private DorisNode node()
    {
        return requireNonNull(node, "node");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(DorisSettings.HOSTS, node().host(), DorisSettings.USER, "lookup", DorisSettings.PASSWORD,
                "lookup-secret", DorisSettings.TIMEOUT, "10000"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("warehouse", values);
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 50));
    }

    @Test
    void theConnectionTestSignsInAndDescribesTheCluster()
    {
        ConnectionResult result = provider.testConnection(config());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("Apache Doris " + version() + ", signed in as 'lookup'@'%', 1 catalog, 3 databases in the internal"
                + " catalog");
        ConnectionResult reader = provider.testConnection(config(DorisSettings.USER, "reader", DorisSettings.PASSWORD, "reader-secret"));
        assertThat(reader.message()).contains("signed in as 'reader'@'%'", "1 database");
    }

    @Test
    void looksUpCatalogsDatabasesTablesColumnsAndWorkloadGroups()
    {
        ServiceConfig config = config();
        assertThat(lookup(config, DorisProvider.CATALOG, "", Map.of())).containsExactly("internal");
        assertThat(lookup(config, DorisProvider.DATABASE, "", Map.of())).containsExactly("hr", "sales", "sales_2026");
        assertThat(lookup(config, DorisProvider.DATABASE, "sa", Map.of(DorisProvider.CATALOG, List.of("internal")))).containsExactly("sales",
                "sales_2026");
        assertThat(lookup(config, DorisProvider.TABLE, "", Map.of(DorisProvider.DATABASE, List.of("sales")))).containsExactly("order_items", "orders");
        assertThat(lookup(config, DorisProvider.TABLE, "order", Map.of(DorisProvider.DATABASE, List.of("sales_*"))))
                .as("an underscore of a policy value is no wildcard").containsExactly("orders");
        assertThat(lookup(config, DorisProvider.COLUMN, "", Map.of(DorisProvider.DATABASE, List.of("sales"), DorisProvider.TABLE, List.of("orders"))))
                .containsExactly("Region", "amount", "id");
        assertThat(lookup(config, DorisProvider.COLUMN, "reg", Map.of(DorisProvider.DATABASE, List.of("s?les*"), DorisProvider.TABLE,
                List.of("orders")))).as("columns compare without case").containsExactly("Region");
        assertThat(lookup(config, DorisProvider.WORKLOAD_GROUP, "", Map.of())).contains("etl", "normal");
        assertThatThrownBy(() -> lookup(config, "partition", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void aLookupUserSeesWhatItHasPrivilegesOn()
    {
        ServiceConfig reader = config(DorisSettings.USER, "reader", DorisSettings.PASSWORD, "reader-secret");
        assertThat(lookup(reader, DorisProvider.DATABASE, "", Map.of())).containsExactly("sales");
        assertThat(lookup(reader, DorisProvider.TABLE, "", Map.of())).containsExactly("orders");
    }

    @Test
    void goesToTheNextFrontendWhenOneCannotBeReached()
    {
        ServiceConfig config = config(DorisSettings.HOSTS, "127.0.0.1:1," + node().host());
        assertThat(lookup(config, DorisProvider.DATABASE, "h", Map.of())).containsExactly("hr");
        ServiceConfig down = config(DorisSettings.HOSTS, "127.0.0.1:1,127.0.0.1:2", DorisSettings.TIMEOUT, "2000");
        assertThatThrownBy(() -> lookup(down, DorisProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(provider.testConnection(down).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void refusedSignInsAreReportedAsSuch()
    {
        ServiceConfig wrong = config(DorisSettings.PASSWORD, "wrong");
        assertThatThrownBy(() -> lookup(wrong, DorisProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ServiceConfig unknown = config(DorisSettings.USER, "ghost", DorisSettings.PASSWORD, "x");
        assertThatThrownBy(() -> lookup(unknown, DorisProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        assertThat(provider.testConnection(wrong).message()).startsWith("Doris refused the sign-in");
    }
}
