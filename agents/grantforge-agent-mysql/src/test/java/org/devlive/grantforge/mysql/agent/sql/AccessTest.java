// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTest
{
    @Test
    void keepsTheResourceLevelUsedForPermissionErrorsAndAudit()
    {
        Access database = new Access("sales", null, null, "create");
        Access table = new Access("sales", "orders", null, "delete");
        Access column = new Access("sales", "orders", "card", "select");

        assertThat(database.level()).isEqualTo("database");
        assertThat(database.resource()).isEqualTo("sales");
        assertThat(table.level()).isEqualTo("table");
        assertThat(table.resource()).isEqualTo("sales.orders");
        assertThat(column.level()).isEqualTo("column");
        assertThat(column.resource()).isEqualTo("sales.orders.card");
    }

    @Test
    void deduplicatesRepeatedReadsWithoutMergingDifferentPermissions()
    {
        Access select = new Access("sales", "orders", "card", "select");
        Access update = new Access("sales", "orders", "card", "update");
        Access anotherTable = new Access("sales", "customers", "card", "select");
        Set<Access> required = new LinkedHashSet<>(List.of(select, new Access("sales", "orders", "card", "select"), update, anotherTable));

        assertThat(required).containsExactly(select, update, anotherTable);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void refusesAResourceThatCannotBeCheckedAtItsDeclaredLevel()
    {
        assertThatThrownBy(() -> new Access("sales", null, "card", "select"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("a column needs its table");
        assertThatNullPointerException().isThrownBy(() -> new Access(null, "orders", null, "select")).withMessage("database");
        assertThatNullPointerException().isThrownBy(() -> new Access("sales", "orders", null, null)).withMessage("accessType");
    }
}
