// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrinoProviderTest
{
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    private final TrinoProvider provider = new TrinoProvider(settings -> new TrinoClient(settings, (url, properties) -> connection,
            (principal, keytab, password, kdcs) -> {
                throw new AssertionError("no Kerberos sign-in expected");
            }));

    private static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(TrinoSettings.URL, "jdbc:trino://trino:8080", TrinoSettings.USER, "lookup"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("warehouse", map);
    }

    private static ResourceDefinition resource(ServiceTypeDefinition definition, String name)
    {
        return definition.resources().stream().filter(resource -> resource.name().equals(name)).findFirst().orElseThrow();
    }

    private static ResultSet catalogs(String... names) throws SQLException
    {
        ResultSet rows = mock(ResultSet.class);
        AtomicInteger row = new AtomicInteger(-1);
        when(rows.next()).thenAnswer(call -> row.incrementAndGet() < names.length);
        when(rows.getString("TABLE_CAT")).thenAnswer(call -> names[row.get()]);
        return rows;
    }

    @Test
    void definesTrinosScopesAndPermissions()
    {
        ServiceTypeDefinition definition = new TrinoProvider().definition();
        assertThat(definition.name()).isEqualTo("trino");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("catalog", "schema", "table", "column", "function",
                "procedure", "session_property", "system_session_property", "user", "system_information");
        assertThat(definition.hierarchies()).contains(List.of("catalog", "schema", "table", "column"), List.of("catalog", "schema", "function"),
                List.of("catalog", "schema", "procedure"), List.of("catalog", "session_property"), List.of("system_session_property"), List.of("user"),
                List.of("system_information"));
        for (String scope : List.of("catalog", "schema", "table")) {
            assertThat(resource(definition, scope).validLeaf()).as(scope).isTrue();
            assertThat(resource(definition, scope).caseSensitive()).as(scope).isFalse();
            assertThat(resource(definition, scope).accessTypes()).as(scope).containsExactlyInAnyOrder("select", "insert", "update", "delete", "create",
                    "drop", "alter", "all");
        }
        assertThat(resource(definition, "column").accessTypes()).containsExactlyInAnyOrder("select", "update");
        assertThat(resource(definition, "function").lookupSupported()).isFalse();
        assertThat(resource(definition, "function").accessTypes()).containsExactlyInAnyOrder("execute", "create_function", "drop_function");
        assertThat(resource(definition, "procedure").accessTypes()).containsExactly("execute");
        assertThat(resource(definition, "session_property").parent()).isEqualTo("catalog");
        assertThat(resource(definition, "system_session_property").accessTypes()).containsExactly("set");
        assertThat(resource(definition, "user").caseSensitive()).isTrue();
        assertThat(resource(definition, "user").lookupSupported()).isFalse();
        assertThat(resource(definition, "user").accessTypes()).containsExactlyInAnyOrder("impersonate", "view_query", "kill_query");
        assertThat(resource(definition, "system_information").accessTypes()).containsExactlyInAnyOrder("read", "write");
        assertThat(definition.accessTypes()).filteredOn(type -> type.name().equals("all")).singleElement()
                .extracting(AccessTypeDefinition::impliedGrants).asInstanceOf(InstanceOfAssertFactories.collection(String.class))
                .containsExactlyInAnyOrder("select", "insert", "update", "delete", "create", "drop", "alter");
        assertThat(definition.policyTypes()).contains(PolicyType.ACCESS, PolicyType.DATA_MASK, PolicyType.ROW_FILTER);
        assertThat(requireNonNull(definition.rowFilter()).resources()).containsExactly("table");
    }

    @Test
    void masksColumnsWithTrinoSql()
    {
        DataMaskDefinition masks = requireNonNull(new TrinoProvider().definition().dataMask());
        assertThat(masks.resources()).containsExactly("column");
        assertThat(masks.maskTypes()).extracting(MaskTypeDefinition::name).containsExactly("mask", "mask_show_last_4", "mask_show_first_4",
                "mask_hash", "mask_null", "mask_none", "mask_date_show_year", "custom");
        assertThat(masks.maskTypes()).filteredOn(type -> !type.name().equals("custom") && !type.name().equals("mask_null"))
                .allSatisfy(type -> assertThat(type.transformer()).contains("{col}"));
        assertThat(masks.maskTypes()).filteredOn(type -> type.name().equals("mask_hash")).singleElement()
                .extracting(MaskTypeDefinition::transformer).isEqualTo("to_hex(sha256(to_utf8({col})))");
    }

    @Test
    void offersTheDriversSettings()
    {
        List<ConfigField> fields = new TrinoProvider().definition().configFields();
        assertThat(fields).extracting(ConfigField::name).containsExactly(TrinoSettings.URL, TrinoSettings.AUTHENTICATION, TrinoSettings.USER,
                TrinoSettings.PASSWORD, TrinoSettings.ACCESS_TOKEN, TrinoSettings.KEYTAB, TrinoSettings.KDC, TrinoSettings.SERVICE_NAME,
                TrinoSettings.CANONICAL_HOST, TrinoSettings.SSL, TrinoSettings.TRUSTSTORE, TrinoSettings.TRUSTSTORE_PASSWORD,
                TrinoSettings.TRUSTSTORE_TYPE, TrinoSettings.VERIFICATION, TrinoSettings.TIMEOUT);
        assertThat(fields).filteredOn(ConfigField::sensitive).extracting(ConfigField::name).containsExactlyInAnyOrder(TrinoSettings.PASSWORD,
                TrinoSettings.ACCESS_TOKEN, TrinoSettings.TRUSTSTORE_PASSWORD);
    }

    @Test
    void checksSettings()
    {
        assertThat(provider.validateConfig(config())).isEmpty();
        assertThat(provider.validateConfig(config(TrinoSettings.USER, ""))).isNotEmpty();
    }

    @Test
    void describesTheCoordinator() throws Exception
    {
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductVersion()).thenReturn("484");
        when(metadata.getCatalogs()).thenAnswer(call -> catalogs("memory", "tpch"));
        assertThat(provider.testConnection(config())).isEqualTo(new ConnectionResult(ConnectionResult.Status.SUCCEEDED, "Trino 484, 2 catalogs"));
        when(metadata.getCatalogs()).thenAnswer(call -> catalogs("system"));
        assertThat(provider.testConnection(config()).message()).isEqualTo("Trino 484, 1 catalog");
    }

    @Test
    void namesWhyTheConnectionFailed() throws Exception
    {
        when(connection.getMetaData()).thenThrow(new SQLException("Authentication failed: Unauthorized"));
        ConnectionResult failed = provider.testConnection(config());
        assertThat(failed.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(failed.message()).isEqualTo("Authentication failed: Unauthorized");
        ConnectionResult invalid = provider.testConnection(config(TrinoSettings.URL, "http://trino"));
        assertThat(invalid.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(invalid.message()).startsWith("jdbc.url: ");
    }

    @Test
    void looksUpThroughTheCoordinator() throws Exception
    {
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getCatalogs()).thenAnswer(call -> catalogs("memory", "tpcds", "tpch"));
        assertThat(provider.lookup(new LookupRequest(config(), "catalog", "tp", Map.of(), 10))).containsExactly("tpcds", "tpch");
    }

    @Test
    void answersSystemInformationItself()
    {
        assertThat(provider.lookup(new LookupRequest(config(), "system_information", "", Map.of(), 10))).containsExactly("*");
        assertThat(provider.lookup(new LookupRequest(config(), "system_information", "x", Map.of(), 10))).isEmpty();
    }

    @Test
    void refusesWhatItCannotLookUp()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config(), "user", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config(TrinoSettings.TIMEOUT, "0"), "catalog", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getMessage()).startsWith("timeout: "));
    }
}
