// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest
{
    private static final TableRef ORDERS = new TableRef("internal", "sales", "orders");
    private static final TableRef ITEMS = new TableRef("internal", "sales", "items");

    @Test
    void readsTheColumnsEachIdentityMayRead()
    {
        Account erin = new Account("erin", List.of("'erin'@'%'", "'erin'@'10.%'"), Set.of("analysts"), Map.of("'erin'@'%'", Set.of("analysts")),
                Map.of("'erin'@'%'", Map.of(ORDERS, Set.of("Region", "id")), "'erin'@'10.%'", Map.of(ORDERS, Set.of("id"), ITEMS, Set.of("sku"))));
        assertThat(erin.readableByAll(ORDERS)).as("in lower case").containsExactly("id");
        assertThat(erin.readableByAny(ORDERS)).containsExactlyInAnyOrder("id", "region");
        assertThat(erin.readableAsShown(ORDERS)).containsExactlyInAnyOrder("id", "Region");
        assertThat(erin.readableByAll(ITEMS)).isEmpty();
        assertThat(erin.columnTables()).containsExactlyInAnyOrder(ORDERS, ITEMS);
    }

    @Test
    void anAccountWithoutIdentitiesReadsNothing()
    {
        Account none = new Account("ghost", List.of(), Set.of(), Map.of(), Map.of());
        assertThat(none.readableByAll(ORDERS)).isEmpty();
        assertThat(none.readableByAny(ORDERS)).isEmpty();
        assertThat(none.columnTables()).isEmpty();
    }
}
