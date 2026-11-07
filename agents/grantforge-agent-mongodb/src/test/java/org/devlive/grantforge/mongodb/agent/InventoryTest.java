// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTest
{
    @Test
    void leavesOutWhatMongoDbKeepsForItself()
    {
        Inventory inventory = new Inventory("rs0", List.of("sales", "admin", "config", "local", "hr"), database -> List.of("orders", "system.views", "items"));
        assertThat(inventory.databases()).containsExactly("hr", "sales");
        assertThat(inventory.collections("sales")).containsExactly("items", "orders");
        assertThat(inventory.deployment()).isEqualTo("rs0");
        assertThat(inventory.sharded()).isFalse();
        assertThat(new Inventory(Inventory.SHARDED, List.of(), database -> List.of()).sharded()).isTrue();
    }

    @Test
    void readsEachDatabasesCollectionsOnce()
    {
        List<String> asked = new ArrayList<>();
        Inventory inventory = new Inventory("rs0", List.of("sales"), database -> {
            asked.add(database);
            return List.of("orders");
        });
        assertThat(asked).isEmpty();
        inventory.collections("sales");
        inventory.collections("sales");
        assertThat(asked).containsExactly("sales");
    }
}
