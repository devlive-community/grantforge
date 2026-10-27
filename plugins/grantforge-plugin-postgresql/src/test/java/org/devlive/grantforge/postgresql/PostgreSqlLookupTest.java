// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgreSqlLookupTest
{
    /** A connection whose queries return the values, one per row. */
    static Connection returning(List<String> values) throws SQLException
    {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        Iterator<String> next = values.iterator();
        String[] current = new String[1];
        when(rows.next()).thenAnswer(ignored -> {
            if (!next.hasNext()) {
                return false;
            }
            current[0] = next.next();
            return true;
        });
        when(rows.getString(1)).thenAnswer(ignored -> current[0]);
        when(statement.executeQuery()).thenReturn(rows);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.createArrayOf(eq("text"), any())).thenReturn(mock(Array.class));
        return connection;
    }

    @Test
    void turnsWildcardsIntoLikePatternsThatMatchNothingElse()
    {
        assertThat(PostgreSqlLookup.like("sales_*")).isEqualTo("sales\\_%");
        assertThat(PostgreSqlLookup.like("a?b%c\\d")).isEqualTo("a_b\\%c\\\\d");
    }

    @Test
    void looksUpDatabasesThatAllowConnections()
    {
        PostgreSqlLookup lookup = PostgreSqlLookup.of(PostgreSqlProvider.DATABASE, " sa ", Map.of(), 10);
        assertThat(lookup.typed()).isEqualTo("sa");
        assertThat(lookup.sql()).isEqualTo("SELECT datname AS value FROM pg_database WHERE datallowconn AND NOT datistemplate"
                + " AND starts_with(datname, ?) ORDER BY value LIMIT ?");
    }

    @Test
    void narrowsSchemasTablesAndColumnsToWhatThePolicyNames()
    {
        PostgreSqlLookup schemas = PostgreSqlLookup.of(PostgreSqlProvider.SCHEMA, "", Map.of(PostgreSqlProvider.DATABASE, List.of(" sales_* ")), 5);
        assertThat(schemas.databases()).containsExactly("sales\\_%");
        assertThat(schemas.sql()).contains("FROM pg_namespace n", PostgreSqlLookup.USER_SCHEMAS).doesNotContain("LIKE ANY");
        PostgreSqlLookup tables = PostgreSqlLookup.of(PostgreSqlProvider.TABLE, "o", Map.of(PostgreSqlProvider.SCHEMA, List.of("crm")), 5);
        assertThat(tables.sql()).contains("FROM pg_class c", "c.relkind IN " + PostgreSqlLookup.RELATION_KINDS, "n.nspname LIKE ANY (?)");
        PostgreSqlLookup columns = PostgreSqlLookup.of(PostgreSqlProvider.COLUMN, "i", Map.of(PostgreSqlProvider.SCHEMA, List.of("crm"),
                PostgreSqlProvider.TABLE, List.of("orders")), 5);
        assertThat(columns.sql()).contains("FROM pg_attribute a", "NOT a.attisdropped", "n.nspname LIKE ANY (?)", "c.relname LIKE ANY (?)");
        assertThat(columns.tables()).containsExactly("orders");
    }

    @Test
    void doesNotNarrowByNoneAnyOrTooManyValues()
    {
        for (List<String> values : List.of(List.<String>of(), List.of("x", "*"), Collections.nCopies(PostgreSqlLookup.MAX_PARENTS + 1, "x"))) {
            PostgreSqlLookup lookup = PostgreSqlLookup.of(PostgreSqlProvider.TABLE, "", Map.of(PostgreSqlProvider.SCHEMA, values), 5);
            assertThat(lookup.schemas()).isEmpty();
            assertThat(lookup.sql()).doesNotContain("LIKE ANY");
        }
    }

    @Test
    void refusesLevelsTheServiceTypeDoesNotHave()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> PostgreSqlLookup.of("function", "", Map.of(), 5)).withMessageContaining("function");
    }

    @Test
    void readsTheDatabasesOfTheHomeDatabaseBindingWhatWasTypedAndTheLimit() throws SQLException
    {
        Connection home = returning(List.of("hr", "sales"));
        PostgreSqlClient client = new PostgreSqlClient(PostgreSqlSettings.of(PostgreSqlSettingsTest.config()), (url, properties) -> home);
        assertThat(PostgreSqlLookup.of(PostgreSqlProvider.DATABASE, "", Map.of(), 7).run(client, "postgres")).containsExactly("hr", "sales");
        PreparedStatement statement = home.prepareStatement("");
        verify(statement).setString(1, "");
        verify(statement).setInt(2, 7);
    }

    @Test
    void readsTheNamesOfEachDatabaseItMayConnectToSortedAndLimited() throws SQLException
    {
        Connection home = returning(List.of("hr", "sales", "sales_2026"));
        Connection sales = returning(List.of("orders", "notes"));
        Connection later = returning(List.of("orders", "archive"));
        PostgreSqlClient client = new PostgreSqlClient(PostgreSqlSettings.of(PostgreSqlSettingsTest.config()), (url, properties) -> {
            if (url.endsWith("/hr")) {
                throw new SQLException("permission denied for database hr", "42501");
            }
            return url.endsWith("/postgres") ? home : url.endsWith("/sales") ? sales : later;
        });
        PostgreSqlLookup lookup = PostgreSqlLookup.of(PostgreSqlProvider.TABLE, "", Map.of(PostgreSqlProvider.DATABASE, List.of("*"),
                PostgreSqlProvider.SCHEMA, List.of("crm")), 3);
        assertThat(lookup.run(client, "postgres")).containsExactly("archive", "notes", "orders");
        PreparedStatement listing = home.prepareStatement("");
        verify(listing).setInt(2, PostgreSqlLookup.MAX_DATABASES);
        verify(sales.prepareStatement("")).setArray(eq(2), any());
        verify(home, never()).createArrayOf(anyString(), any());
    }

    @Test
    void failsWhenNoDatabaseItReadsMayBeConnectedTo() throws SQLException
    {
        Connection home = returning(List.of("hr"));
        PostgreSqlClient client = new PostgreSqlClient(PostgreSqlSettings.of(PostgreSqlSettingsTest.config()), (url, properties) -> {
            if (url.endsWith("/hr")) {
                throw new SQLException("permission denied for database hr", "42501");
            }
            return home;
        });
        PostgreSqlLookup lookup = PostgreSqlLookup.of(PostgreSqlProvider.SCHEMA, "", Map.of(PostgreSqlProvider.DATABASE, List.of("hr")), 5);
        assertThatThrownBy(() -> lookup.run(client, "postgres")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        verify(home).createArrayOf(eq("text"), any());

        Connection again = returning(List.of("hr"));
        PostgreSqlClient broken = new PostgreSqlClient(PostgreSqlSettings.of(PostgreSqlSettingsTest.config()), (url, properties) -> {
            if (url.endsWith("/hr")) {
                throw new SQLException("out of memory", "53200");
            }
            return again;
        });
        assertThatThrownBy(() -> lookup.run(broken, "postgres")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
    }

    @Test
    void aDatabaseDroppedSinceItWasListedShowsNothing() throws SQLException
    {
        Connection home = returning(List.of("gone", "sales"));
        Connection sales = returning(List.of("crm"));
        PostgreSqlClient client = new PostgreSqlClient(PostgreSqlSettings.of(PostgreSqlSettingsTest.config()), (url, properties) -> {
            if (url.endsWith("/gone")) {
                throw new SQLException("database \"gone\" does not exist", "3D000");
            }
            return url.endsWith("/sales") ? sales : home;
        });
        assertThat(PostgreSqlLookup.of(PostgreSqlProvider.SCHEMA, "", Map.of(), 5).run(client, "postgres")).containsExactly("crm");
    }
}
