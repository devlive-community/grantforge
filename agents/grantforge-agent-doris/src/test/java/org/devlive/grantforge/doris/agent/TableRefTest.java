// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableRefTest
{
    @Test
    void namesTheTableForStatementsAndEvents()
    {
        TableRef table = new TableRef("internal", "sales", "odd`name");
        assertThat(table.sql()).isEqualTo("`internal`.`sales`.`odd``name`");
        assertThat(table).hasToString("internal.sales.odd`name");
    }

    @Test
    void sortsByCatalogDatabaseAndName()
    {
        List<TableRef> sorted = List.of(new TableRef("internal", "b", "a"), new TableRef("hive", "z", "z"), new TableRef("internal", "a", "b"))
                .stream().sorted(TableRef.ORDER).toList();
        assertThat(sorted).extracting(TableRef::toString).containsExactly("hive.z.z", "internal.a.b", "internal.b.a");
    }
}
