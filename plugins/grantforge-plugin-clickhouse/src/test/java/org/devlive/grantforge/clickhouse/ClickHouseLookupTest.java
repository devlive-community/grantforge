// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

class ClickHouseLookupTest
{
    @Test
    void turnsWildcardsIntoLikePatternsThatMatchNothingElse()
    {
        assertThat(ClickHouseLookup.like("sales_*")).isEqualTo("sales\\_%");
        assertThat(ClickHouseLookup.like("a?b%c\\d")).isEqualTo("a_b\\%c\\\\d");
    }

    @Test
    void looksUpDatabasesByWhatWasTyped()
    {
        ClickHouseLookup lookup = ClickHouseLookup.of(ClickHouseProvider.DATABASE, " sa ", Map.of(), 10);
        assertThat(lookup.sql()).startsWith("SELECT name AS value FROM system.databases").endsWith("ORDER BY value LIMIT {limit:UInt32}");
        assertThat(lookup.parameters()).containsEntry("typed", "sa").containsEntry("limit", 10)
                .containsEntry("system", ClickHouseLookup.SYSTEM_DATABASES);
    }

    @Test
    void narrowsTablesAndColumnsToTheDatabasesAndTablesThePolicyNames()
    {
        ClickHouseLookup tables = ClickHouseLookup.of(ClickHouseProvider.TABLE, "", Map.of(ClickHouseProvider.DATABASE, List.of(" sales_* ", "hr")), 5);
        assertThat(tables.sql()).contains("FROM system.tables", "arrayExists(pattern -> database LIKE pattern, {databases:Array(String)})");
        assertThat(tables.parameters()).containsEntry("databases", List.of("sales\\_%", "hr"));
        ClickHouseLookup columns = ClickHouseLookup.of(ClickHouseProvider.COLUMN, "i", Map.of(ClickHouseProvider.DATABASE, List.of("sales"),
                ClickHouseProvider.TABLE, List.of("orders")), 5);
        assertThat(columns.sql()).contains("FROM system.columns", "database LIKE pattern", "table LIKE pattern");
        assertThat(columns.parameters()).containsEntry("tables", List.of("orders"));
    }

    @Test
    void doesNotNarrowByNoneAnyOrTooManyValues()
    {
        for (List<String> values : List.of(List.<String>of(), List.of("x", "*"), Collections.nCopies(ClickHouseLookup.MAX_PARENTS + 1, "x"))) {
            ClickHouseLookup lookup = ClickHouseLookup.of(ClickHouseProvider.TABLE, "", Map.of(ClickHouseProvider.DATABASE, values), 5);
            assertThat(lookup.sql()).doesNotContain("arrayExists");
            assertThat(lookup.parameters()).doesNotContainKey("databases");
        }
    }

    @Test
    void theServerHasItsOneValue()
    {
        assertThat(ClickHouseLookup.of(ClickHouseProvider.SERVER, "", Map.of(), 5).run(mock(ClickHouseNodeClient.class))).containsExactly("*");
        assertThat(ClickHouseLookup.of(ClickHouseProvider.SERVER, "*", Map.of(), 5).run(mock(ClickHouseNodeClient.class))).containsExactly("*");
        assertThat(ClickHouseLookup.of(ClickHouseProvider.SERVER, "s", Map.of(), 5).run(mock(ClickHouseNodeClient.class))).isEmpty();
    }

    @Test
    void refusesLevelsTheServiceTypeDoesNotHave()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> ClickHouseLookup.of("schema", "", Map.of(), 5)).withMessageContaining("schema");
    }
}
