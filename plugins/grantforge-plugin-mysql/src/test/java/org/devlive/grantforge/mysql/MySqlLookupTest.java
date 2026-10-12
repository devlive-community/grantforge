// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MySqlLookupTest
{
    @Test
    void listsDatabasesStartingWithWhatWasTyped()
    {
        MySqlLookup query = MySqlLookup.of("database", " sal ", Map.of(), 25);
        assertThat(query.sql()).isEqualTo("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE ? ESCAPE '!'"
                + " ORDER BY SCHEMA_NAME LIMIT ?");
        assertThat(query.parameters()).containsExactly("sal%", 25);
    }

    @Test
    void narrowsTablesToTheDatabasesOfThePolicy()
    {
        MySqlLookup query = MySqlLookup.of("table", "ord", Map.of("database", List.of("sales", "hr_*")), 10);
        assertThat(query.sql()).isEqualTo("SELECT DISTINCT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_NAME LIKE ? ESCAPE '!'"
                + " AND (TABLE_SCHEMA LIKE ? ESCAPE '!' OR TABLE_SCHEMA LIKE ? ESCAPE '!') ORDER BY TABLE_NAME LIMIT ?");
        assertThat(query.parameters()).containsExactly("ord%", "sales", "hr!_%", 10);
    }

    @Test
    void narrowsColumnsToTheDatabasesAndTablesOfThePolicy()
    {
        MySqlLookup query = MySqlLookup.of("column", "", Map.of("database", List.of("sales"), "table", List.of("order?")), 5);
        assertThat(query.sql()).isEqualTo("SELECT DISTINCT COLUMN_NAME FROM information_schema.COLUMNS WHERE COLUMN_NAME LIKE ? ESCAPE '!'"
                + " AND (TABLE_SCHEMA LIKE ? ESCAPE '!') AND (TABLE_NAME LIKE ? ESCAPE '!') ORDER BY COLUMN_NAME LIMIT ?");
        assertThat(query.parameters()).containsExactly("%", "sales", "order_", 5);
    }

    @Test
    void doesNotNarrowByAnyOrTooManyValues()
    {
        assertThat(MySqlLookup.of("table", "", Map.of("database", List.of("sales", "*")), 5).parameters()).containsExactly("%", 5);
        assertThat(MySqlLookup.of("table", "", Map.of("database", List.of()), 5).parameters()).containsExactly("%", 5);
        List<String> many = Collections.nCopies(MySqlLookup.MAX_PARENTS + 1, "sales");
        assertThat(MySqlLookup.of("column", "", Map.of("database", many), 5).sql()).doesNotContain("TABLE_SCHEMA");
    }

    @Test
    void typedTextMatchesOnlyItself()
    {
        assertThat(MySqlLookup.escape("a_b%c!d")).isEqualTo("a!_b!%c!!d");
        assertThat(MySqlLookup.of("database", "50%_off", Map.of(), 1).parameters()).first().isEqualTo("50!%!_off%");
        assertThat(MySqlLookup.like("*_log?")).isEqualTo("%!_log_");
    }

    @Test
    void knowsOnlyItsLevels()
    {
        assertThatThrownBy(() -> MySqlLookup.of("schema", "", Map.of(), 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema");
    }
}
