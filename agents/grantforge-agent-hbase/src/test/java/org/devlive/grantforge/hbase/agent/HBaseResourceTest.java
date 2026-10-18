// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.apache.hadoop.hbase.TableName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HBaseResourceTest
{
    @Test
    void namesEachScopeFromTheTop()
    {
        HBaseResource column = HBaseResource.table(TableName.valueOf("sales:orders")).family("d").column("ssn");
        assertThat(column.scopes()).containsExactly(Map.of("namespace", "sales"), Map.of("namespace", "sales", "table", "orders"),
                Map.of("namespace", "sales", "table", "orders", "column_family", "d"),
                Map.of("namespace", "sales", "table", "orders", "column_family", "d", "column", "ssn"));
        assertThat(column.level()).isEqualTo("column");
        assertThat(column.display()).isEqualTo("sales:orders/d:ssn");
        assertThat(column.values()).containsExactly("sales", "orders", "d", "ssn");
        assertThat(column.namespace()).isEqualTo("sales");
        assertThat(column).hasToString("sales:orders/d:ssn");
    }

    @Test
    void putsTablesWithoutANamespaceInTheDefaultOne()
    {
        HBaseResource table = HBaseResource.table(TableName.valueOf("events"));
        assertThat(table.display()).isEqualTo("default:events");
        assertThat(table.level()).isEqualTo("table");
        assertThat(table.family("e").display()).isEqualTo("default:events/e");
        assertThat(table.family("e").level()).isEqualTo("column_family");
        assertThat(HBaseResource.namespace("sales").display()).isEqualTo("sales");
        assertThat(HBaseResource.namespace("sales").level()).isEqualTo("namespace");
    }

    @Test
    void knowsTheGlobalScope()
    {
        HBaseResource global = HBaseResource.global();
        assertThat(global.scopes()).containsExactly(Map.of("global", "*"));
        assertThat(global.level()).isEqualTo("global");
        assertThat(global.display()).isEqualTo("*");
        assertThat(global.namespace()).isNull();
        assertThat(global.values()).isEqualTo(List.of("*"));
        assertThatThrownBy(() -> global.family("d")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void namesWhatIsBelowOnlyWhereItCanBe()
    {
        assertThatThrownBy(() -> HBaseResource.namespace("sales").family("d")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("below sales");
        assertThatThrownBy(() -> HBaseResource.table(TableName.valueOf("sales:orders")).column("ssn")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void comparesByScopeAndValues()
    {
        HBaseResource first = HBaseResource.table(TableName.valueOf("sales:orders")).family("d");
        HBaseResource second = HBaseResource.table(TableName.valueOf("sales:orders")).family("d");
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(HBaseResource.table(TableName.valueOf("sales:orders")));
        assertThat(HBaseResource.global()).isNotEqualTo(HBaseResource.namespace("*"));
        assertThat(first).isNotEqualTo("sales:orders/d");
    }
}
