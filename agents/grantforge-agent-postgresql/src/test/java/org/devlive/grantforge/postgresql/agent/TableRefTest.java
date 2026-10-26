// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TableRefTest
{
    @Test
    void namesTheTableInStatementsAndMessages()
    {
        TableRef table = new TableRef("Crm", "odd \"table\"");
        assertThat(table.sql()).isEqualTo("\"Crm\".\"odd \"\"table\"\"\"");
        assertThat(table).hasToString("Crm.odd \"table\"");
    }

    @Test
    void sortsBySchemaThenName()
    {
        List<TableRef> sorted = Stream.of(new TableRef("b", "a"), new TableRef("a", "z"), new TableRef("a", "b")).sorted(TableRef.ORDER).toList();
        assertThat(sorted).containsExactly(new TableRef("a", "b"), new TableRef("a", "z"), new TableRef("b", "a"));
    }
}
