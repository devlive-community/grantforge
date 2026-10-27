// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgreSqlProviderTest
{
    private static ResourceDefinition resource(ServiceTypeDefinition definition, String name)
    {
        return definition.resources().stream().filter(resource -> resource.name().equals(name)).findFirst().orElseThrow();
    }

    private static PostgreSqlProvider describing(long databases) throws SQLException
    {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("version")).thenReturn("17.11 (Debian 17.11-1.pgdg13+2)");
        when(row.getString("signed_in")).thenReturn("lookup");
        when(row.getLong("databases")).thenReturn(databases);
        when(statement.executeQuery()).thenReturn(row);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        return new PostgreSqlProvider(settings -> new PostgreSqlClient(settings, (url, properties) -> connection));
    }

    @Test
    void definesPostgreSqlsLevelsAndPrivileges()
    {
        ServiceTypeDefinition definition = new PostgreSqlProvider().definition();
        assertThat(definition.name()).isEqualTo("postgresql");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("database", "schema", "table", "column");
        assertThat(resource(definition, "column").accessTypes()).containsExactlyInAnyOrder("select", "insert", "update");
        assertThat(resource(definition, "table").accessTypes()).containsExactlyInAnyOrder("delete", "truncate", "trigger");
        assertThat(resource(definition, "schema").accessTypes()).containsExactly("create");
        assertThat(resource(definition, "database").accessTypes()).containsExactlyInAnyOrder("create_schema", "temporary");
        assertThat(definition.resources()).allSatisfy(level -> assertThat(level.caseSensitive()).isTrue());
        assertThat(definition.rowFilter()).isNotNull();
        assertThat(definition.dataMask()).as("PostgreSQL has no column masking").isNull();
        assertThat(definition.configFields()).extracting(ConfigField::name).containsExactly(PostgreSqlSettings.HOSTS, PostgreSqlSettings.DATABASE,
                PostgreSqlSettings.AUTHENTICATION, PostgreSqlSettings.USER, PostgreSqlSettings.PASSWORD, PostgreSqlSettings.SSL_MODE,
                PostgreSqlSettings.ROOT_CERTIFICATE, PostgreSqlSettings.CLIENT_CERTIFICATE, PostgreSqlSettings.CLIENT_KEY, PostgreSqlSettings.TIMEOUT);
    }

    @Test
    void validatesTheSettings()
    {
        assertThat(new PostgreSqlProvider().validateConfig(PostgreSqlSettingsTest.config())).isEmpty();
        assertThat(new PostgreSqlProvider().validateConfig(PostgreSqlSettingsTest.config(PostgreSqlSettings.HOSTS, "x:0"))).hasSize(1);
    }

    @Test
    void theConnectionTestDescribesTheServer() throws SQLException
    {
        ConnectionResult result = describing(3).testConnection(PostgreSqlSettingsTest.config());
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("PostgreSQL 17.11, signed in as lookup, 3 databases");
        assertThat(describing(1).testConnection(PostgreSqlSettingsTest.config()).message()).endsWith("1 database");
    }

    @Test
    void theConnectionTestReportsFailuresAndUnusableSettings()
    {
        PostgreSqlProvider refused = new PostgreSqlProvider(settings -> new PostgreSqlClient(settings, (url, properties) -> {
            throw new SQLException("password authentication failed for user \"lookup\"", "28P01");
        }));
        ConnectionResult failed = refused.testConnection(PostgreSqlSettingsTest.config());
        assertThat(failed.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(failed.message()).isEqualTo("pg1.example.com:5432/postgres: password authentication failed for user \"lookup\"");
        ConnectionResult invalid = refused.testConnection(PostgreSqlSettingsTest.config(PostgreSqlSettings.HOSTS, PostgreSqlSettingsTest.UNSET));
        assertThat(invalid.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(invalid.message()).startsWith(PostgreSqlSettings.HOSTS);
    }

    @Test
    void readsTheReleaseOfAServerVersion()
    {
        assertThat(PostgreSqlProvider.version("18.6")).isEqualTo("18.6");
        assertThat(PostgreSqlProvider.version("16.15 (Ubuntu 16.15-1.pgdg24.04+1)")).isEqualTo("16.15");
    }

    @Test
    void looksUpThroughTheClient() throws SQLException
    {
        Connection connection = PostgreSqlLookupTest.returning(List.of("hr", "sales"));
        PostgreSqlProvider provider = new PostgreSqlProvider(settings -> new PostgreSqlClient(settings, (url, properties) -> connection));
        assertThat(provider.lookup(new LookupRequest(PostgreSqlSettingsTest.config(), PostgreSqlProvider.DATABASE, "", Map.of(), 10)))
                .containsExactly("hr", "sales");
    }

    @Test
    void refusesALookupOfUnusableSettingsOrALevelItDoesNotHave()
    {
        PostgreSqlProvider provider = new PostgreSqlProvider();
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(PostgreSqlSettingsTest.config(PostgreSqlSettings.USER, PostgreSqlSettingsTest.UNSET),
                PostgreSqlProvider.DATABASE, "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                        failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(PostgreSqlSettingsTest.config(), "sequence", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }
}
