// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class MasksTest
{
    private static DataMask mask(String type, @Nullable String value)
    {
        Snapshot snapshot = new TrinoSnapshots().mask(List.of("alice"), type, value, "catalog", "hive", "schema", "hr", "table", "people", "column",
                "name").snapshot();
        return requireNonNull(snapshot.mask(AccessRequest.builder("alice", "select").resource("catalog", "hive").resource("schema", "hr")
                .resource("table", "people").resource("column", "name").build()), "mask");
    }

    @Test
    void quotesColumns()
    {
        assertThat(Masks.quote("name")).isEqualTo("\"name\"");
        assertThat(Masks.quote("odd\"name")).isEqualTo("\"odd\"\"name\"");
    }

    @Test
    void masksTextColumnsAndCastsBackToTheirType()
    {
        assertThat(Masks.expression(mask("mask_hash", null), "name", "varchar(20)")).isEqualTo("CAST((to_hex(sha256(to_utf8(\"name\")))) AS varchar(20))");
        assertThat(Masks.expression(mask("mask", null), "name", "char(3)")).startsWith("CAST((regexp_replace(").endsWith(") AS char(3))");
        assertThat(Masks.expression(mask("mask_hash", null), "name", "VARCHAR")).endsWith(" AS VARCHAR)");
    }

    @Test
    void nullsColumnsATextMaskDoesNotFit()
    {
        assertThat(Masks.expression(mask("mask_hash", null), "salary", "bigint")).isEqualTo("CAST(NULL AS bigint)");
        assertThat(Masks.expression(mask("mask", null), "tags", "array(varchar)")).isEqualTo("CAST(NULL AS array(varchar))");
    }

    @Test
    void showsOnlyTheYearOfDatesAndTimestamps()
    {
        assertThat(Masks.expression(mask("mask_date_show_year", null), "born", "date")).isEqualTo("CAST((date_trunc('year', \"born\")) AS date)");
        assertThat(Masks.expression(mask("mask_date_show_year", null), "seen", "timestamp(3) with time zone")).endsWith("AS timestamp(3) with time zone)");
        assertThat(Masks.expression(mask("mask_date_show_year", null), "name", "varchar")).isEqualTo("CAST(NULL AS varchar)");
    }

    @Test
    void castsCustomExpressionsToTheType()
    {
        assertThat(Masks.expression(mask("custom", "round({col}, -3)"), "salary", "bigint")).isEqualTo("CAST((round(\"salary\", -3)) AS bigint)");
    }

    @Test
    void nullsOrKeepsAsThePolicySays()
    {
        assertThat(Masks.expression(mask("mask_null", null), "salary", "decimal(10,2)")).isEqualTo("CAST(NULL AS decimal(10,2))");
        assertThat(Masks.expression(mask("mask_none", null), "salary", "bigint")).isNull();
    }

    @Test
    void hidesWhatItCannotRead()
    {
        assertThat(Masks.expression(mask("opaque", null), "name", "varchar")).as("no transformer").isEqualTo("CAST(NULL AS varchar)");
        assertThat(Masks.expression(mask("custom", null), "name", "varchar")).as("a custom mask without its expression")
                .isEqualTo("CAST(NULL AS varchar)");
    }
}
