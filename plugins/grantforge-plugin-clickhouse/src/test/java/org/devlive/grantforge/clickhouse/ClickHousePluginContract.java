// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ClickHouse plugin against a real server of one ClickHouse release: the connection test, lookups of databases,
 * tables and columns within what a policy names, what a lookup user without privileges sees, going to the next server
 * when one is down, refused sign-ins, and https with a CA certificate and with a client certificate as the sign-in.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class ClickHousePluginContract
{
    private final ClickHouseProvider provider = new ClickHouseProvider();
    private @Nullable ClickHouseNode node;

    /** The certificates; the server starts before the instance's own directories exist. */
    @TempDir
    static Path files;

    /**
     * The release under test.
     *
     * @return such as {@code 26.8.34.13}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        ClickHouseNode started = new ClickHouseNode(version(), files);
        node = started;
        started.run("CREATE DATABASE sales", "CREATE DATABASE hr", "CREATE DATABASE `sales_2026`",
                "CREATE TABLE sales.orders (id UInt32, region String, amount Float64) ENGINE = MergeTree ORDER BY id",
                "CREATE TABLE sales.order_items (order_id UInt32, sku String) ENGINE = MergeTree ORDER BY order_id",
                "CREATE TABLE hr.salaries (employee String, amount Float64) ENGINE = MergeTree ORDER BY employee",
                "CREATE TABLE sales_2026.orders (id UInt32, total Float64) ENGINE = MergeTree ORDER BY id",
                "CREATE USER reader IDENTIFIED BY 'reader-secret'", "GRANT SELECT(id) ON sales.orders TO reader",
                "CREATE USER nobody IDENTIFIED BY 'nobody-secret'",
                "CREATE USER " + ClickHouseNode.CERTIFICATE_USER + " IDENTIFIED WITH ssl_certificate CN '" + ClickHouseNode.CERTIFICATE_USER + "'",
                "GRANT SHOW ON hr.* TO " + ClickHouseNode.CERTIFICATE_USER);
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
    }

    private ClickHouseNode node()
    {
        return requireNonNull(node, "node");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(ClickHouseSettings.URLS, node().url(), ClickHouseSettings.USER, ClickHouseNode.ADMIN,
                ClickHouseSettings.PASSWORD, ClickHouseNode.ADMIN_PASSWORD, ClickHouseSettings.TIMEOUT, "10000"));
        for (int index = 0; index < more.length; index += 2) {
            if (more[index + 1].isEmpty()) {
                values.remove(more[index]);
            }
            else {
                values.put(more[index], more[index + 1]);
            }
        }
        return new ServiceConfig("warehouse", values);
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 50));
    }

    @Test
    void theConnectionTestSignsInAndDescribesTheServer()
    {
        ConnectionResult result = provider.testConnection(config());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).startsWith("ClickHouse " + version()).contains("signed in as admin", "4 databases");
        ConnectionResult reader = provider.testConnection(config(ClickHouseSettings.USER, "reader", ClickHouseSettings.PASSWORD, "reader-secret"));
        assertThat(reader.message()).contains("signed in as reader", "1 database");
    }

    @Test
    void looksUpDatabasesTablesAndColumnsWithinWhatThePolicyNames()
    {
        ServiceConfig config = config();
        assertThat(lookup(config, ClickHouseProvider.DATABASE, "", Map.of())).containsExactly("default", "hr", "sales", "sales_2026");
        assertThat(lookup(config, ClickHouseProvider.DATABASE, "sa", Map.of())).containsExactly("sales", "sales_2026");
        assertThat(lookup(config, ClickHouseProvider.TABLE, "", Map.of(ClickHouseProvider.DATABASE, List.of("sales"))))
                .containsExactly("order_items", "orders");
        assertThat(lookup(config, ClickHouseProvider.TABLE, "order", Map.of(ClickHouseProvider.DATABASE, List.of("sales_*"))))
                .as("an underscore of a policy value is no wildcard").containsExactly("orders");
        assertThat(lookup(config, ClickHouseProvider.TABLE, "", Map.of(ClickHouseProvider.DATABASE, List.of("*")))).containsExactly("order_items",
                "orders", "salaries");
        assertThat(lookup(config, ClickHouseProvider.COLUMN, "", Map.of(ClickHouseProvider.DATABASE, List.of("sales"), ClickHouseProvider.TABLE,
                List.of("orders")))).containsExactly("amount", "id", "region");
        assertThat(lookup(config, ClickHouseProvider.COLUMN, "", Map.of(ClickHouseProvider.DATABASE, List.of("s?les*"), ClickHouseProvider.TABLE,
                List.of("orders")))).containsExactly("amount", "id", "region", "total");
        assertThat(lookup(config, ClickHouseProvider.SERVER, "", Map.of())).containsExactly("*");
        assertThat(lookup(config, ClickHouseProvider.SERVER, "x", Map.of())).isEmpty();
        assertThatThrownBy(() -> lookup(config, "system", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void aLookupUserSeesWhatItHasPrivilegesOn()
    {
        ServiceConfig reader = config(ClickHouseSettings.USER, "reader", ClickHouseSettings.PASSWORD, "reader-secret");
        assertThat(lookup(reader, ClickHouseProvider.DATABASE, "", Map.of())).containsExactly("sales");
        assertThat(lookup(reader, ClickHouseProvider.TABLE, "", Map.of())).containsExactly("orders");
        assertThat(lookup(reader, ClickHouseProvider.COLUMN, "", Map.of())).containsExactly("id");
        ServiceConfig nobody = config(ClickHouseSettings.USER, "nobody", ClickHouseSettings.PASSWORD, "nobody-secret");
        assertThat(lookup(nobody, ClickHouseProvider.DATABASE, "", Map.of())).isEmpty();
    }

    @Test
    void goesToTheNextServerWhenOneCannotBeReached()
    {
        ServiceConfig config = config(ClickHouseSettings.URLS, "http://127.0.0.1:1," + node().url());
        assertThat(lookup(config, ClickHouseProvider.DATABASE, "h", Map.of())).containsExactly("hr");
        ServiceConfig down = config(ClickHouseSettings.URLS, "http://127.0.0.1:1,http://127.0.0.1:2");
        assertThatThrownBy(() -> lookup(down, ClickHouseProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE)).hasMessageContaining("127.0.0.1:1");
        assertThat(provider.testConnection(down).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void refusedSignInsAreReportedAsSuch()
    {
        ServiceConfig wrong = config(ClickHouseSettings.PASSWORD, "wrong");
        assertThatThrownBy(() -> lookup(wrong, ClickHouseProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ServiceConfig unknown = config(ClickHouseSettings.USER, "ghost", ClickHouseSettings.PASSWORD, "x");
        assertThatThrownBy(() -> lookup(unknown, ClickHouseProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ConnectionResult result = provider.testConnection(wrong);
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).contains(node().url());
    }

    @Test
    void signsInOverHttpsWithAPasswordOrAClientCertificate()
    {
        ServiceConfig password = config(ClickHouseSettings.URLS, node().secureUrl(), ClickHouseSettings.ROOT_CERTIFICATE, node().caCertificate());
        assertThat(lookup(password, ClickHouseProvider.DATABASE, "h", Map.of())).containsExactly("hr");
        ServiceConfig certificate = config(ClickHouseSettings.URLS, node().secureUrl(), ClickHouseSettings.ROOT_CERTIFICATE, node().caCertificate(),
                ClickHouseSettings.AUTHENTICATION, ClickHouseSettings.CERTIFICATE_SIGN_IN, ClickHouseSettings.USER, ClickHouseNode.CERTIFICATE_USER,
                ClickHouseSettings.PASSWORD, "", ClickHouseSettings.CLIENT_CERTIFICATE, node().clientCertificate(), ClickHouseSettings.CLIENT_KEY,
                node().clientKey());
        assertThat(provider.testConnection(certificate).message()).contains("signed in as " + ClickHouseNode.CERTIFICATE_USER, "1 database");
        assertThat(lookup(certificate, ClickHouseProvider.TABLE, "", Map.of())).containsExactly("salaries");
        ServiceConfig untrusted = config(ClickHouseSettings.URLS, node().secureUrl());
        assertThatThrownBy(() -> lookup(untrusted, ClickHouseProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }
}
