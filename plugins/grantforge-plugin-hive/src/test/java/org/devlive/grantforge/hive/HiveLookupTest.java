// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiveLookupTest
{
    private final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    private final Connection connection = mock(Connection.class);

    HiveLookupTest() throws SQLException
    {
        when(connection.getMetaData()).thenReturn(metadata);
    }

    /** A result set of one column's values, closed once read. */
    private static ResultSet rows(String column, @Nullable String... values) throws SQLException
    {
        ResultSet rows = mock(ResultSet.class);
        int[] at = {-1};
        when(rows.next()).thenAnswer(call -> ++at[0] < values.length);
        when(rows.getString(column)).thenAnswer(call -> values[at[0]]);
        return rows;
    }

    @Test
    void listsDatabasesStartingWithWhatWasTypedWithoutCase() throws SQLException
    {
        ResultSet databases = rows("TABLE_SCHEM", "sales", "Sales_Archive", "salaries", "hr");
        when(metadata.getSchemas(null, "sal%")).thenReturn(databases);
        assertThat(HiveLookup.of("database", " SAL", Map.of(), 10).run(connection)).containsExactly("salaries", "sales", "sales_archive");
        verify(databases).close();
    }

    @Test
    void listsTablesWithinTheDatabasesOfThePolicy() throws SQLException
    {
        ResultSet answer1 = rows("TABLE_NAME", "orders", "order_items");
        when(metadata.getTables(null, "sales", "ord%", null)).thenReturn(answer1);
        ResultSet answer2 = rows("TABLE_NAME", "ordinals");
        when(metadata.getTables(null, "hr_%", "ord%", null)).thenReturn(answer2);
        assertThat(HiveLookup.of("table", "ord", Map.of("database", List.of("Sales", "hr_*")), 2).run(connection))
                .containsExactly("order_items", "orders");
    }

    @Test
    void listsColumnsOfEveryDatabaseAndTablePattern() throws SQLException
    {
        ResultSet answer3 = rows("COLUMN_NAME", "id", "card", null);
        when(metadata.getColumns(null, "sales", "order_", "%")).thenReturn(answer3);
        assertThat(HiveLookup.of("column", "", Map.of("database", List.of("sales"), "table", List.of("order?")), 10).run(connection))
                .containsExactly("card", "id");
    }

    @Test
    void listsUdfsByTheirOwnNames() throws SQLException
    {
        ResultSet answer4 = rows("FUNCTION_NAME", "sales.my_hash", "hr.my_upper", "mask", "upper");
        when(metadata.getFunctions(null, null, "%.my%")).thenReturn(answer4);
        assertThat(HiveLookup.of("udf", "my", Map.of("database", List.of("*")), 10).run(connection)).as("built-in functions have no database")
                .containsExactly("my_hash", "my_upper");
    }

    @Test
    void looksEverywhereForNoneAnyOrTooManyParents()
    {
        assertThat(HiveLookup.of("table", "", Map.of(), 5).databases()).containsExactly("%");
        assertThat(HiveLookup.of("table", "", Map.of("database", List.of()), 5).databases()).containsExactly("%");
        assertThat(HiveLookup.of("table", "", Map.of("database", Collections.nCopies(HiveLookup.MAX_PARENTS + 1, "sales")), 5).databases())
                .containsExactly("%");
        assertThat(HiveLookup.of("column", "", Map.of("database", List.of("a", "a")), 5).databases()).containsExactly("a");
        assertThat(HiveLookup.pattern("Sales_*?")).isEqualTo("sales_%_");
    }

    @Test
    void knowsOnlyTheLevelsWithNames()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> HiveLookup.of("url", "", Map.of(), 1)).withMessageContaining("url");
    }

    @Test
    void closesWhatTheServerReturnsWhenReadingFails() throws SQLException
    {
        ResultSet broken = mock(ResultSet.class);
        when(broken.next()).thenThrow(new SQLException("lost"));
        when(metadata.getSchemas(any(), anyString())).thenReturn(broken);
        HiveLookup lookup = HiveLookup.of("database", "", Map.of(), 1);
        assertThatThrownBy(() -> lookup.run(connection)).isInstanceOf(SQLException.class);
        verify(broken).close();
    }
}
