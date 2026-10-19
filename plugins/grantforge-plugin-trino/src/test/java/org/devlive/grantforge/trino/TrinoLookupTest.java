// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrinoLookupTest
{
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metadata = mock(DatabaseMetaData.class);

    /** A row whose value is SQL NULL. */
    private static final String NULL = "<null>";

    /** Rows of one column, the stand-in of what the driver answers; {@link #NULL} stands for SQL NULL. */
    private static ResultSet rows(String column, String... values) throws SQLException
    {
        ResultSet rows = mock(ResultSet.class);
        AtomicInteger row = new AtomicInteger(-1);
        when(rows.next()).thenAnswer(call -> row.incrementAndGet() < values.length);
        when(rows.getString(column)).thenAnswer(call -> NULL.equals(values[row.get()]) ? null : values[row.get()]);
        return rows;
    }

    @BeforeEach
    void connect() throws SQLException
    {
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getCatalogs()).thenAnswer(call -> rows("TABLE_CAT", "hive", "Memory", "system", "tpch"));
    }

    private static TrinoLookup lookup(String resource, String typed, Map<String, List<String>> context)
    {
        return TrinoLookup.of(resource, typed, context, 50);
    }

    @Test
    void looksUpTheLevelsItKnows()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> lookup("function", "", Map.of())).withMessageEndingWith("not function");
        assertThatIllegalArgumentException().isThrownBy(() -> lookup("user", "", Map.of()));
        assertThat(lookup("catalog", " Sales ", Map.of()).typed()).isEqualTo("sales");
    }

    @Test
    void narrowsByThePolicysValues()
    {
        TrinoLookup lookup = lookup("table", "", Map.of("catalog", List.of(" Hive ", "hive", "TPCH"), "schema", List.of("*", "sales"), "table",
                List.of()));
        assertThat(lookup.catalogs()).containsExactly("hive", "tpch");
        assertThat(lookup.schemas()).as("a wildcard among the values looks everywhere").containsExactly("*");
        assertThat(lookup.tables()).containsExactly("*");
        List<String> many = IntStream.rangeClosed(0, TrinoLookup.MAX_PARENTS).mapToObj(index -> "c" + index).toList();
        assertThat(lookup("schema", "", Map.of("catalog", many)).catalogs()).as("too many look everywhere").containsExactly("*");
        assertThat(lookup("schema", "", Map.of("catalog", many.subList(0, TrinoLookup.MAX_PARENTS))).catalogs()).hasSize(TrinoLookup.MAX_PARENTS);
    }

    @Test
    void turnsWildcardsIntoPatterns()
    {
        assertThat(TrinoLookup.pattern("sales_*").matcher("SALES_2026").matches()).isTrue();
        assertThat(TrinoLookup.pattern("sales_*").matcher("salesx2026").matches()).isFalse();
        assertThat(TrinoLookup.pattern("h?").matcher("hr").matches()).isTrue();
        assertThat(TrinoLookup.pattern("h?").matcher("hrx").matches()).isFalse();
        assertThat(TrinoLookup.pattern("a.b").matcher("axb").matches()).as("a dot is no wildcard").isFalse();
        assertThat(TrinoLookup.pattern("*").matcher("line\nbreak").matches()).isTrue();
    }

    @Test
    void turnsWildcardsIntoLikePatterns()
    {
        assertThat(TrinoLookup.like("sales*", "\\")).isEqualTo("sales%");
        assertThat(TrinoLookup.like("h?", "\\")).isEqualTo("h_");
        assertThat(TrinoLookup.like("order_items", "\\")).isEqualTo("order\\_items");
        assertThat(TrinoLookup.like("100%", "\\")).isEqualTo("100\\%");
        assertThat(TrinoLookup.like("a\\b", "\\")).isEqualTo("a\\\\b");
        assertThat(TrinoLookup.like("", "\\")).isEmpty();
    }

    @Test
    void listsCatalogsByWhatWasTyped() throws SQLException
    {
        assertThat(lookup("catalog", "", Map.of()).run(connection)).containsExactly("hive", "memory", "system", "tpch");
        assertThat(lookup("catalog", "M", Map.of()).run(connection)).containsExactly("memory");
        assertThat(TrinoLookup.of("catalog", "", Map.of(), 2).run(connection)).containsExactly("hive", "memory");
    }

    @Test
    void listsSchemasOfTheCatalogsThePolicyNames() throws SQLException
    {
        when(metadata.getSchemas("hive", "s%")).thenAnswer(call -> rows("TABLE_SCHEM", "sales", "Staging"));
        when(metadata.getSchemas("memory", "s%")).thenAnswer(call -> rows("TABLE_SCHEM", "sales", "shop"));
        assertThat(lookup("schema", "s", Map.of("catalog", List.of("hive", "mem*"))).run(connection)).containsExactly("sales", "shop", "staging");
        verify(metadata, never()).getSchemas("tpch", "s%");
    }

    @Test
    void listsNothingForCatalogsTrinoDoesNotHave() throws SQLException
    {
        assertThat(lookup("schema", "", Map.of("catalog", List.of("nothing"))).run(connection)).isEmpty();
        verify(metadata, never()).getSchemas(anyString(), anyString());
    }

    @Test
    void listsTablesOfTheSchemasThePolicyNames() throws SQLException
    {
        when(metadata.getTables("hive", "sales", "order\\_%", null)).thenAnswer(call -> rows("TABLE_NAME", "order_items"));
        when(metadata.getTables("hive", "s_", "order\\_%", null)).thenAnswer(call -> rows("TABLE_NAME", "order_lines"));
        assertThat(lookup("table", "order_", Map.of("catalog", List.of("hive"), "schema", List.of("sales", "s?"))).run(connection))
                .containsExactly("order_items", "order_lines");
    }

    @Test
    void listsColumnsOfTheTablesThePolicyNames() throws SQLException
    {
        when(metadata.getColumns("tpch", "%", "orders", "%")).thenAnswer(call -> rows("COLUMN_NAME", "orderkey", "custkey", NULL));
        assertThat(lookup("column", "", Map.of("catalog", List.of("tpch"), "table", List.of("orders"))).run(connection))
                .containsExactly("custkey", "orderkey");
        verify(metadata).getColumns(any(), any(), any(), any());
    }

    @Test
    void listsAtMostSoManyCatalogsToLookIn() throws SQLException
    {
        String[] names = IntStream.range(0, TrinoLookup.MAX_PARENTS + 5).mapToObj(index -> "c" + (100 + index)).toArray(String[]::new);
        when(metadata.getCatalogs()).thenAnswer(call -> rows("TABLE_CAT", names));
        List<String> asked = new ArrayList<>();
        when(metadata.getSchemas(anyString(), anyString())).thenAnswer(call -> {
            asked.add(call.getArgument(0));
            return rows("TABLE_SCHEM");
        });
        lookup("schema", "", Map.of()).run(connection);
        assertThat(asked).hasSize(TrinoLookup.MAX_PARENTS).containsExactlyElementsOf(Arrays.asList(names).subList(0, TrinoLookup.MAX_PARENTS));
    }

    private void session(String... names) throws SQLException
    {
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SHOW SESSION")).thenAnswer(call -> rows("Name", names));
    }

    @Test
    void listsSystemSessionProperties() throws SQLException
    {
        session("query_max_run_time", "hive.compression_codec", NULL, "Join_Distribution_Type");
        assertThat(lookup("system_session_property", "", Map.of()).run(connection)).containsExactly("join_distribution_type", "query_max_run_time");
        assertThat(lookup("system_session_property", "query", Map.of()).run(connection)).containsExactly("query_max_run_time");
    }

    @Test
    void listsTheSessionPropertiesOfTheCatalogsThePolicyNames() throws SQLException
    {
        session("query_max_run_time", "hive.compression_codec", "iceberg.Compression_Codec", "tpch.split_size");
        assertThat(lookup("session_property", "", Map.of("catalog", List.of("hive", "ice*"))).run(connection)).containsExactly("compression_codec");
        assertThat(lookup("session_property", "s", Map.of()).run(connection)).containsExactly("split_size");
        verify(metadata, never()).getSchemas(any(), isNull());
    }
}
