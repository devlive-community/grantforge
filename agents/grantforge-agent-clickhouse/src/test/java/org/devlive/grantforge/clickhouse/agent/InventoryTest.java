// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTest
{
    @Test
    void holdsDatabasesSortedAndReadsColumnsOncePerDatabase()
    {
        List<String> read = new ArrayList<>();
        Inventory inventory = new Inventory(Map.of("sales", List.of("orders", "items"), "empty", List.of()), database -> {
            read.add(database);
            return Map.of("orders", List.of("id", "card"));
        });
        assertThat(inventory.databases()).containsExactly("empty", "sales");
        assertThat(inventory.tables("sales")).containsExactly("orders", "items");
        assertThat(inventory.tables("hr")).isEmpty();
        assertThat(inventory.columns(new TableRef("sales", "orders"))).containsExactly("id", "card");
        assertThat(inventory.columns(new TableRef("sales", "items"))).isEmpty();
        assertThat(read).containsExactly("sales");
    }
}
