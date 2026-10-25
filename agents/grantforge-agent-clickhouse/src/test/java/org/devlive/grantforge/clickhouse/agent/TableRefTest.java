// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableRefTest
{
    @Test
    void quotesForStatementsAndNotForEvents()
    {
        TableRef table = new TableRef("sales", "odd\"one");
        assertThat(table.sql()).isEqualTo("\"sales\".\"odd\"\"one\"");
        assertThat(table).hasToString("sales.odd\"one");
    }

    @Test
    void sortsByDatabaseThenTable()
    {
        assertThat(TableRef.ORDER.compare(new TableRef("a", "z"), new TableRef("b", "a"))).isNegative();
        assertThat(TableRef.ORDER.compare(new TableRef("a", "b"), new TableRef("a", "a"))).isPositive();
        assertThat(TableRef.ORDER.compare(new TableRef("a", "a"), new TableRef("a", "a"))).isZero();
    }
}
