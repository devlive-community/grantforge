// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HiveCheckTest
{
    @Test
    void namesAResourceFromItsRoot()
    {
        HiveCheck column = new HiveCheck("select", "database", "sales", "table", "orders", "column", "card");
        assertThat(column.resource()).containsExactly(Map.entry("database", "sales"), Map.entry("table", "orders"), Map.entry("column", "card"));
        assertThat(column.level()).isEqualTo("column");
        assertThat(column.display()).isEqualTo("sales.orders.card");
        assertThat(column.table()).containsExactly("sales", "orders");
        assertThat(column).hasToString("select on sales.orders.card");
        assertThat(new HiveCheck("select", "database", "sales").table()).isEmpty();
        assertThat(new HiveCheck("read", "url", "hdfs://nn/data").display()).isEqualTo("hdfs://nn/data");
    }

    @Test
    void buildsTheRequestOfAUser()
    {
        HiveCheck table = new HiveCheck("update", "database", "sales", "table", "orders");
        AccessRequest request = table.request("alice", List.of("analysts"), "10.0.0.1");
        assertThat(request.user()).isEqualTo("alice");
        assertThat(request.accessType()).isEqualTo("update");
        assertThat(request.groups()).containsExactly("analysts");
        assertThat(request.resource()).containsEntry("table", "orders");
        assertThat(request.context()).containsEntry("clientAddress", "10.0.0.1");
        assertThat(table.request("alice", List.of(), null).context()).isEmpty();
        assertThat(table.builder("alice", List.of(), null).below(true).build().below()).isTrue();
    }

    @Test
    void comparesByResourceAndAccess()
    {
        HiveCheck one = new HiveCheck("select", "database", "sales");
        assertThat(one).isEqualTo(new HiveCheck("select", "database", "sales")).hasSameHashCodeAs(new HiveCheck("select", "database", "sales"));
        assertThat(one).isNotEqualTo(new HiveCheck("drop", "database", "sales")).isNotEqualTo(new HiveCheck("select", "database", "hr"))
                .isNotEqualTo("select on sales");
    }

    @Test
    void refusesMissingValues()
    {
        assertThatThrownBy(() -> new HiveCheck("select")).hasMessageContaining("names levels");
        assertThatThrownBy(() -> new HiveCheck("select", "database")).hasMessageContaining("names levels");
        assertThatThrownBy(() -> new HiveCheck("select", "database", "")).hasMessageContaining("names no database");
    }
}
