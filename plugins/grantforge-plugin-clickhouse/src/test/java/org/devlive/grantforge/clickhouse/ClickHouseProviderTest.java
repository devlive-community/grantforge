// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.query.GenericRecord;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClickHouseProviderTest
{
    private final Client clickhouse = mock(Client.class);
    private final ClickHouseProvider provider = new ClickHouseProvider(settings -> new ClickHouseNodeClient(settings, url -> clickhouse));

    private static ResourceDefinition resource(ServiceTypeDefinition definition, String name)
    {
        return definition.resources().stream().filter(resource -> resource.name().equals(name)).findFirst().orElseThrow();
    }

    private void describes(long databases)
    {
        GenericRecord row = mock(GenericRecord.class);
        when(row.getString("version")).thenReturn("26.8.34.13");
        when(row.getString("user")).thenReturn("lookup");
        when(row.getLong("databases")).thenReturn(databases);
        when(clickhouse.queryAll(contains("version()"), anyMap(), any())).thenReturn(List.of(row));
    }

    @Test
    void definesClickHousesLevelsAndPrivileges()
    {
        ServiceTypeDefinition definition = provider.definition();
        assertThat(definition.name()).isEqualTo("clickhouse");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("database", "table", "column", "server");
        assertThat(resource(definition, "column").accessTypes()).containsExactlyInAnyOrder("select", "insert");
        assertThat(resource(definition, "table").accessTypes()).containsExactlyInAnyOrder("alter", "drop", "truncate", "optimize");
        assertThat(resource(definition, "database").accessTypes()).containsExactlyInAnyOrder("create", "drop_database");
        assertThat(resource(definition, "server").accessTypes()).containsExactlyInAnyOrder("create_database", "kill_query", "system");
        assertThat(definition.resources()).allSatisfy(level -> assertThat(level.caseSensitive()).isTrue());
        assertThat(definition.rowFilter()).isNotNull();
        assertThat(definition.dataMask()).as("ClickHouse masks columns in its cloud only").isNull();
        assertThat(definition.configFields()).extracting(ConfigField::name).contains(ClickHouseSettings.URLS, ClickHouseSettings.USER,
                ClickHouseSettings.PASSWORD, ClickHouseSettings.CLIENT_KEY);
    }

    @Test
    void validatesTheSettings()
    {
        assertThat(provider.validateConfig(ClickHouseSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(ClickHouseSettingsTest.config(ClickHouseSettings.URLS, "nope"))).isNotEmpty();
    }

    @Test
    void theConnectionTestDescribesTheServer()
    {
        describes(1);
        assertThat(provider.testConnection(ClickHouseSettingsTest.config()).message()).isEqualTo("ClickHouse 26.8.34.13, signed in as lookup, 1 database");
        describes(3);
        ConnectionResult result = provider.testConnection(ClickHouseSettingsTest.config());
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).endsWith("3 databases");
    }

    @Test
    void theConnectionTestReportsFailuresAndUnusableSettings()
    {
        when(clickhouse.queryAll(anyString(), anyMap(), any())).thenThrow(new ServerException(516, "Code: 516. bad password", 403, "q"));
        ConnectionResult refused = provider.testConnection(ClickHouseSettingsTest.config());
        assertThat(refused.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(refused.message()).isEqualTo("http://ch1.example.com:8123: Code: 516. bad password");
        ConnectionResult invalid = provider.testConnection(ClickHouseSettingsTest.config(ClickHouseSettings.TIMEOUT, "0"));
        assertThat(invalid.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(invalid.message()).startsWith(ClickHouseSettings.TIMEOUT);
    }

    @Test
    void looksUpThroughTheServers()
    {
        GenericRecord row = mock(GenericRecord.class);
        when(row.getString("value")).thenReturn("orders");
        when(clickhouse.queryAll(contains("system.tables"), eq(Map.of("typed", "o", "system", ClickHouseLookup.SYSTEM_DATABASES, "limit", 7)), any()))
                .thenReturn(List.of(row));
        assertThat(provider.lookup(new LookupRequest(ClickHouseSettingsTest.config(), "table", "o", Map.of(), 7))).containsExactly("orders");
        assertThat(provider.lookup(new LookupRequest(ClickHouseSettingsTest.config(), "server", "", Map.of(), 7))).containsExactly("*");
    }

    @Test
    void refusesLookupsItCannotMake()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(ClickHouseSettingsTest.config(), "schema", "", Map.of(), 5)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(ClickHouseSettingsTest.config(ClickHouseSettings.URLS, "x"), "table", "", Map.of(), 5)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void theHostLoadsTheDefaultProvider()
    {
        assertThat(new ClickHouseProvider().definition().name()).isEqualTo(ClickHouseProvider.TYPE);
    }
}
