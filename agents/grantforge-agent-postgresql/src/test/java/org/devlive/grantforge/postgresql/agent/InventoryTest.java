// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class InventoryTest
{
    private static Inventory inventory()
    {
        return Inventory.of(List.of(ServerStateTest.column("crm", "orders", "r", "id", "orders_id_seq"),
                ServerStateTest.column("crm", "orders", "r", "id", null), ServerStateTest.column("crm", "orders", "r", "region", null),
                Row.of("schema", "crm", "name", "empty", "kind", "p", "owner", "postgres", "column", null),
                ServerStateTest.column("public", "orders", "v", "id", null), Row.of("schema", "lonely", "name", null)));
    }

    private static Inventory.Table table(Inventory inventory, TableRef ref)
    {
        return requireNonNull(inventory.table(ref), ref.toString());
    }

    @Test
    void readsSchemasTablesColumnsAndSerialSequences()
    {
        Inventory inventory = inventory();
        assertThat(inventory.schemas()).containsExactly("crm", "lonely", "public");
        assertThat(inventory.tables("crm")).extracting(table -> table.ref().table()).containsExactly("empty", "orders");
        assertThat(inventory.tables("missing")).isEmpty();
        TableRef orders = new TableRef("crm", "orders");
        assertThat(inventory.columns(orders)).as("a column joined twice is one column").containsExactly("id", "region");
        assertThat(inventory.columns(new TableRef("crm", "missing"))).isEmpty();
        assertThat(inventory.columns(new TableRef("nowhere", "orders"))).isEmpty();
        assertThat(table(inventory, new TableRef("crm", "empty")).columns()).isEmpty();
        assertThat(table(inventory, orders).owner()).isEqualTo("postgres");
        assertThat(table(inventory, orders).sequences()).containsOnlyKeys("id");
        assertThat(Inventory.EMPTY.schemas()).isEmpty();
    }

    @Test
    void findsTablesAndSequencesByName()
    {
        Inventory inventory = inventory();
        assertThat(inventory.named("orders")).containsExactly(new TableRef("crm", "orders"), new TableRef("public", "orders"));
        assertThat(inventory.named("nothing")).isEmpty();
        TableRef sequence = new TableRef("crm", "orders_id_seq");
        assertThat(inventory.namedSequence("orders_id_seq")).containsExactly(sequence);
        assertThat(inventory.namedSequence("other_seq")).isEmpty();
        assertThat(inventory.filledBy(sequence)).isEqualTo(new TableRef("crm", "orders"));
        assertThat(inventory.filledBy(new TableRef("crm", "other_seq"))).isNull();
    }

    @Test
    void filtersTheRowsOfTablesOnly()
    {
        Inventory inventory = inventory();
        assertThat(table(inventory, new TableRef("crm", "orders")).filterable()).isTrue();
        assertThat(table(inventory, new TableRef("crm", "empty")).filterable()).as("a partitioned table").isTrue();
        assertThat(table(inventory, new TableRef("public", "orders")).filterable()).as("a view").isFalse();
    }
}
