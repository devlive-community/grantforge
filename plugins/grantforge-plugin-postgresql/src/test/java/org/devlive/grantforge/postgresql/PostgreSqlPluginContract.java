// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

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
 * The PostgreSQL plugin against a real server of one PostgreSQL release: the connection test, lookups of databases,
 * schemas, tables and columns within what a policy names, databases the lookup user may not connect to, going to the
 * next server when one is down, refused sign-ins, and TLS with a CA certificate and with a client certificate as the
 * sign-in.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class PostgreSqlPluginContract
{
    private final PostgreSqlProvider provider = new PostgreSqlProvider();
    private @Nullable PostgreSqlNode node;

    /** The certificates; the server starts before the instance's own directories exist. */
    @TempDir
    static Path files;

    /**
     * The release under test.
     *
     * @return such as {@code 17.11}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        PostgreSqlNode started = new PostgreSqlNode(version(), files);
        node = started;
        started.run("postgres", "CREATE DATABASE sales", "CREATE DATABASE hr", "CREATE DATABASE sales_2026",
                "CREATE USER reader PASSWORD 'reader-secret'", "CREATE USER " + PostgreSqlNode.CERTIFICATE_USER,
                "REVOKE CONNECT ON DATABASE hr FROM PUBLIC");
        started.run("sales", "CREATE SCHEMA crm", "CREATE SCHEMA crm_archive",
                "CREATE TABLE crm.orders (id serial PRIMARY KEY, region text, amount numeric)",
                "CREATE TABLE crm.order_items (order_id int, sku text)", "CREATE VIEW crm.regions AS SELECT DISTINCT region FROM crm.orders",
                "CREATE TABLE crm_archive.orders (id int, total numeric)", "CREATE TABLE public.notes (body text)");
        started.run("hr", "CREATE TABLE public.salaries (employee text, amount numeric)");
        started.run("sales_2026", "CREATE SCHEMA crm", "CREATE TABLE crm.orders (id int, total numeric)");
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
    }

    private PostgreSqlNode node()
    {
        return requireNonNull(node, "node");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(PostgreSqlSettings.HOSTS, node().host(), PostgreSqlSettings.USER, PostgreSqlNode.ADMIN,
                PostgreSqlSettings.PASSWORD, PostgreSqlNode.ADMIN_PASSWORD, PostgreSqlSettings.TIMEOUT, "10000"));
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
        assertThat(result.message()).isEqualTo("PostgreSQL " + version() + ", signed in as postgres, 4 databases");
        ConnectionResult reader = provider.testConnection(config(PostgreSqlSettings.USER, "reader", PostgreSqlSettings.PASSWORD, "reader-secret",
                PostgreSqlSettings.DATABASE, "sales"));
        assertThat(reader.message()).contains("signed in as reader");
    }

    @Test
    void looksUpDatabasesSchemasTablesAndColumnsWithinWhatThePolicyNames()
    {
        ServiceConfig config = config();
        assertThat(lookup(config, PostgreSqlProvider.DATABASE, "", Map.of())).containsExactly("hr", "postgres", "sales", "sales_2026");
        assertThat(lookup(config, PostgreSqlProvider.DATABASE, "sa", Map.of())).containsExactly("sales", "sales_2026");
        assertThat(lookup(config, PostgreSqlProvider.SCHEMA, "", Map.of(PostgreSqlProvider.DATABASE, List.of("sales"))))
                .as("PostgreSQL's own schemas are left out").containsExactly("crm", "crm_archive", "public");
        assertThat(lookup(config, PostgreSqlProvider.SCHEMA, "crm_", Map.of(PostgreSqlProvider.DATABASE, List.of("sales"))))
                .containsExactly("crm_archive");
        assertThat(lookup(config, PostgreSqlProvider.TABLE, "", Map.of(PostgreSqlProvider.DATABASE, List.of("sales"), PostgreSqlProvider.SCHEMA,
                List.of("crm")))).as("views are tables to policies").containsExactly("order_items", "orders", "regions");
        assertThat(lookup(config, PostgreSqlProvider.TABLE, "", Map.of(PostgreSqlProvider.DATABASE, List.of("sales_*"), PostgreSqlProvider.SCHEMA,
                List.of("crm")))).as("an underscore of a policy value is no wildcard").containsExactly("orders");
        assertThat(lookup(config, PostgreSqlProvider.TABLE, "", Map.of(PostgreSqlProvider.DATABASE, List.of("*"))))
                .containsExactly("notes", "order_items", "orders", "regions", "salaries");
        assertThat(lookup(config, PostgreSqlProvider.COLUMN, "", Map.of(PostgreSqlProvider.DATABASE, List.of("sales"), PostgreSqlProvider.SCHEMA,
                List.of("crm"), PostgreSqlProvider.TABLE, List.of("orders")))).containsExactly("amount", "id", "region");
        assertThat(lookup(config, PostgreSqlProvider.COLUMN, "", Map.of(PostgreSqlProvider.DATABASE, List.of("s?les*"), PostgreSqlProvider.SCHEMA,
                List.of("crm*"), PostgreSqlProvider.TABLE, List.of("orders")))).containsExactly("amount", "id", "region", "total");
        assertThatThrownBy(() -> lookup(config, "function", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void aLookupUserSeesTheDatabasesItMayConnectTo()
    {
        ServiceConfig reader = config(PostgreSqlSettings.USER, "reader", PostgreSqlSettings.PASSWORD, "reader-secret");
        assertThat(lookup(reader, PostgreSqlProvider.DATABASE, "", Map.of())).as("PostgreSQL lists every database")
                .containsExactly("hr", "postgres", "sales", "sales_2026");
        assertThat(lookup(reader, PostgreSqlProvider.TABLE, "", Map.of())).as("hr refuses the reader's connection")
                .containsExactly("notes", "order_items", "orders", "regions");
        assertThatThrownBy(() -> lookup(reader, PostgreSqlProvider.TABLE, "", Map.of(PostgreSqlProvider.DATABASE, List.of("hr"))))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        ServiceConfig missing = config(PostgreSqlSettings.DATABASE, "nowhere");
        assertThatThrownBy(() -> lookup(missing, PostgreSqlProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.NOT_FOUND));
    }

    @Test
    void goesToTheNextServerWhenOneCannotBeReached()
    {
        ServiceConfig config = config(PostgreSqlSettings.HOSTS, "127.0.0.1:1," + node().host());
        assertThat(lookup(config, PostgreSqlProvider.DATABASE, "h", Map.of())).containsExactly("hr");
        ServiceConfig down = config(PostgreSqlSettings.HOSTS, "127.0.0.1:1,127.0.0.1:2");
        assertThatThrownBy(() -> lookup(down, PostgreSqlProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE)).hasMessageContaining("127.0.0.1:1");
        assertThat(provider.testConnection(down).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void refusedSignInsAreReportedAsSuch()
    {
        ServiceConfig wrong = config(PostgreSqlSettings.PASSWORD, "wrong");
        assertThatThrownBy(() -> lookup(wrong, PostgreSqlProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ServiceConfig unknown = config(PostgreSqlSettings.USER, "ghost", PostgreSqlSettings.PASSWORD, "x");
        assertThatThrownBy(() -> lookup(unknown, PostgreSqlProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ConnectionResult result = provider.testConnection(wrong);
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).contains(node().host());
    }

    @Test
    void signsInOverTlsWithAPasswordOrAClientCertificate()
    {
        ServiceConfig password = config(PostgreSqlSettings.HOSTS, node().secureHost(), PostgreSqlSettings.SSL_MODE, "verify-full",
                PostgreSqlSettings.ROOT_CERTIFICATE, node().caCertificate());
        assertThat(lookup(password, PostgreSqlProvider.DATABASE, "h", Map.of())).containsExactly("hr");
        ServiceConfig certificate = config(PostgreSqlSettings.HOSTS, node().secureHost(), PostgreSqlSettings.SSL_MODE, "verify-full",
                PostgreSqlSettings.ROOT_CERTIFICATE, node().caCertificate(), PostgreSqlSettings.AUTHENTICATION, PostgreSqlSettings.CERTIFICATE_SIGN_IN,
                PostgreSqlSettings.USER, PostgreSqlNode.CERTIFICATE_USER, PostgreSqlSettings.PASSWORD, "", PostgreSqlSettings.CLIENT_CERTIFICATE,
                node().clientCertificate(), PostgreSqlSettings.CLIENT_KEY, node().clientKey());
        assertThat(provider.testConnection(certificate).message()).contains("signed in as " + PostgreSqlNode.CERTIFICATE_USER);
        assertThat(lookup(certificate, PostgreSqlProvider.SCHEMA, "", Map.of(PostgreSqlProvider.DATABASE, List.of("sales_2026"))))
                .containsExactly("crm", "public");
        ServiceConfig untrusted = config(PostgreSqlSettings.HOSTS, node().secureHost(), PostgreSqlSettings.SSL_MODE, "verify-full");
        assertThatThrownBy(() -> lookup(untrusted, PostgreSqlProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        ServiceConfig noCertificate = config(PostgreSqlSettings.HOSTS, node().secureHost(), PostgreSqlSettings.SSL_MODE, "require",
                PostgreSqlSettings.USER, PostgreSqlNode.CERTIFICATE_USER, PostgreSqlSettings.PASSWORD, "x");
        assertThatThrownBy(() -> lookup(noCertificate, PostgreSqlProvider.DATABASE, "", Map.of())).as("cert authentication wants a certificate")
                .isInstanceOfSatisfying(LookupException.class,
                        failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }
}
