// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrinoResourceTest
{
    @Test
    void decidesTheCatalogTreeAtEveryScope()
    {
        TrinoResource column = TrinoResource.column("hive", "sales", "orders", "amount");
        assertThat(column.level()).isEqualTo("column");
        assertThat(column.path()).containsExactly(Map.entry("catalog", "hive"), Map.entry("schema", "sales"), Map.entry("table", "orders"),
                Map.entry("column", "amount"));
        assertThat(column.scopes()).containsExactly(Map.of("catalog", "hive"), Map.of("catalog", "hive", "schema", "sales"),
                Map.of("catalog", "hive", "schema", "sales", "table", "orders"), column.path());
        assertThat(column.display()).isEqualTo("hive.sales.orders.amount");
        assertThat(column.value("table")).isEqualTo("orders");
        assertThat(column.value("function")).isNull();
        assertThat(TrinoResource.catalog("hive").scopes()).containsExactly(Map.of("catalog", "hive"));
        assertThat(TrinoResource.schema("hive", "sales").scopes()).hasSize(2);
        assertThat(TrinoResource.table("hive", "sales", "orders").scopes()).hasSize(3);
    }

    @Test
    void decidesRoutinesAndPropertiesWhereTheyAre()
    {
        TrinoResource function = TrinoResource.function("hive", "udf", "clean");
        assertThat(function.scopes()).containsExactly(Map.of("catalog", "hive", "schema", "udf", "function", "clean"));
        assertThat(function.display()).isEqualTo("hive.udf.clean");
        assertThat(TrinoResource.procedure("system", "runtime", "kill_query").display()).isEqualTo("system.runtime.kill_query");
        assertThat(TrinoResource.procedure("system", "runtime", "kill_query").scopes()).hasSize(1);
        TrinoResource property = TrinoResource.sessionProperty("hive", "compression_codec");
        assertThat(property.level()).isEqualTo("session_property");
        assertThat(property.scopes()).hasSize(1);
        assertThat(property.display()).isEqualTo("session property hive.compression_codec");
        assertThat(TrinoResource.systemSessionProperty("query_max_run_time").display()).isEqualTo("system session property query_max_run_time");
        assertThat(TrinoResource.user("bob").display()).isEqualTo("user bob");
        assertThat(TrinoResource.user("bob").scopes()).containsExactly(Map.of("user", "bob"));
        assertThat(TrinoResource.systemInformation().path()).containsExactly(Map.entry("system_information", "*"));
        assertThat(TrinoResource.systemInformation().display()).hasToString("system information");
    }

    @Test
    void comparesByWhatItNames()
    {
        assertThat(TrinoResource.table("a", "b", "c")).isEqualTo(TrinoResource.table("a", "b", "c")).hasSameHashCodeAs(TrinoResource.table("a", "b",
                "c")).isNotEqualTo(TrinoResource.table("a", "b", "d")).isNotEqualTo("a.b.c");
        assertThat(List.of(TrinoResource.user("x"))).contains(TrinoResource.user("x"));
        assertThat(TrinoResource.schema("a", "b")).hasToString("a.b");
    }
}
