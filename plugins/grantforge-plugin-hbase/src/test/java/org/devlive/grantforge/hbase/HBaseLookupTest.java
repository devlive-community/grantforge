// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HBaseLookupTest
{
    private final Admin admin = mock(Admin.class);

    @BeforeEach
    void cluster() throws Exception
    {
        when(admin.listNamespaceDescriptors()).thenReturn(new NamespaceDescriptor[] {NamespaceDescriptor.create("sales").build(),
                NamespaceDescriptor.create("sales_eu").build(), NamespaceDescriptor.create("hr").build()});
        when(admin.listTableNamesByNamespace("sales")).thenReturn(new TableName[] {TableName.valueOf("sales", "orders"),
                TableName.valueOf("sales", "items")});
        when(admin.listTableNamesByNamespace("sales_eu")).thenReturn(new TableName[] {TableName.valueOf("sales_eu", "orders")});
        when(admin.listTableNamesByNamespace("hr")).thenReturn(new TableName[] {TableName.valueOf("hr", "salaries")});
        family("sales:orders", "d", "meta");
        family("sales:items", "d");
        family("sales_eu:orders", "eu");
        family("hr:salaries", "p");
    }

    private void family(String table, String... families) throws Exception
    {
        TableDescriptor descriptor = mock(TableDescriptor.class);
        Set<byte[]> names = new TreeSet<>(Bytes.BYTES_COMPARATOR);
        for (String family : families) {
            names.add(Bytes.toBytes(family));
        }
        when(descriptor.getColumnFamilyNames()).thenReturn(names);
        when(admin.getDescriptor(TableName.valueOf(table))).thenReturn(descriptor);
    }

    private List<String> lookup(String resource, String typed, Map<String, List<String>> context) throws Exception
    {
        return HBaseLookup.of(resource, typed, context, 10).run(admin);
    }

    @Test
    void listsNamespacesByPrefix() throws Exception
    {
        assertThat(lookup("namespace", "", Map.of())).containsExactly("hr", "sales", "sales_eu");
        assertThat(lookup("namespace", " sales_ ", Map.of())).containsExactly("sales_eu");
        assertThat(HBaseLookup.of("namespace", "", Map.of(), 2).run(admin)).containsExactly("hr", "sales");
    }

    @Test
    void listsTablesOfTheNamespacesAPolicyNames() throws Exception
    {
        assertThat(lookup("table", "", Map.of())).containsExactly("items", "orders", "salaries");
        assertThat(lookup("table", "", Map.of("namespace", List.of("sales")))).containsExactly("items", "orders");
        assertThat(lookup("table", "o", Map.of("namespace", List.of("sales*")))).containsExactly("orders");
        assertThat(lookup("table", "", Map.of("namespace", List.of("h?")))).containsExactly("salaries");
        assertThat(lookup("table", "", Map.of("namespace", List.of("sales", "*")))).as("any value looks everywhere")
                .containsExactly("items", "orders", "salaries");
        assertThat(lookup("table", "", Map.of("namespace", List.of("sales", "hr"), "table", List.of("orders")))).as("tables list all tables")
                .containsExactly("items", "orders", "salaries");
    }

    @Test
    void listsColumnFamiliesOfTheTablesAPolicyNames() throws Exception
    {
        assertThat(lookup("column_family", "", Map.of("namespace", List.of("sales"), "table", List.of("orders")))).containsExactly("d", "meta");
        assertThat(lookup("column_family", "", Map.of("namespace", List.of("sales*"), "table", List.of("orders")))).containsExactly("d", "eu", "meta");
        assertThat(lookup("column_family", "m", Map.of("namespace", List.of("*"), "table", List.of("*")))).containsExactly("meta");
        assertThat(lookup("column_family", "", Map.of("namespace", List.of("hr"), "table", List.of("none")))).isEmpty();
    }

    @Test
    void looksEverywhereForTooManyParentsAndReadsAtMostSomeTables() throws Exception
    {
        List<String> many = IntStream.range(0, HBaseLookup.MAX_PARENTS + 1).mapToObj(index -> "ns" + index).toList();
        assertThat(lookup("table", "", Map.of("namespace", many))).containsExactly("items", "orders", "salaries");
        TableName[] tables = IntStream.range(0, HBaseLookup.MAX_TABLES + 5).mapToObj(index -> TableName.valueOf("hr", "t" + index))
                .toArray(TableName[]::new);
        when(admin.listTableNamesByNamespace("hr")).thenReturn(tables);
        TableDescriptor empty = mock(TableDescriptor.class);
        when(empty.getColumnFamilyNames()).thenReturn(Collections.emptySet());
        when(admin.getDescriptor(any(TableName.class))).thenReturn(empty);
        assertThat(lookup("column_family", "", Map.of("namespace", List.of("hr"), "table", List.of("t*")))).isEmpty();
        verify(admin, times(HBaseLookup.MAX_TABLES)).getDescriptor(any(TableName.class));
    }

    @Test
    void readsWildcardsLiterallyButForTheirStarsAndQuestionMarks()
    {
        assertThat(HBaseLookup.pattern("sales_eu*").matcher("sales_eu-2").matches()).isTrue();
        assertThat(HBaseLookup.pattern("sales_eu*").matcher("salesXeu").matches()).isFalse();
        assertThat(HBaseLookup.pattern("a.c").matcher("abc").matches()).as("a dot is a dot").isFalse();
        assertThat(HBaseLookup.pattern("a?c").matcher("abc").matches()).isTrue();
        assertThat(HBaseLookup.pattern("(a)").matcher("(a)").matches()).isTrue();
        assertThat(HBaseLookup.pattern("Orders").matcher("orders").matches()).as("with case").isFalse();
    }

    @Test
    void looksUpOnlyNamespacesTablesAndColumnFamilies()
    {
        assertThatThrownBy(() -> HBaseLookup.of("column", "", Map.of(), 10)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not column");
    }
}
