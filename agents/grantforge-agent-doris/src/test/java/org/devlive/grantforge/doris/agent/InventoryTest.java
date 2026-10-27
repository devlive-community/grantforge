// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTest
{
    @Test
    void readsEachLevelOnceWhenFirstAskedFor()
    {
        AtomicInteger reads = new AtomicInteger();
        Inventory inventory = new Inventory(List.of("internal", "hive"), List.of("normal"), catalog -> {
            reads.incrementAndGet();
            return List.of(catalog + "_db");
        }, (catalog, database) -> {
            reads.incrementAndGet();
            return List.of(database + "_t");
        }, table -> {
            reads.incrementAndGet();
            return List.of(table.table() + "_c");
        });
        assertThat(inventory.catalogs()).containsExactly("internal", "hive");
        assertThat(inventory.workloadGroups()).containsExactly("normal");
        assertThat(reads).hasValue(0);
        assertThat(inventory.databases("hive")).containsExactly("hive_db");
        assertThat(inventory.databases("hive")).containsExactly("hive_db");
        assertThat(inventory.tables("hive", "hive_db")).containsExactly("hive_db_t");
        assertThat(inventory.tables("hive", "hive_db")).containsExactly("hive_db_t");
        assertThat(inventory.columns(new TableRef("hive", "hive_db", "t"))).containsExactly("t_c");
        assertThat(inventory.columns(new TableRef("hive", "hive_db", "t"))).containsExactly("t_c");
        assertThat(reads).hasValue(3);
    }
}
