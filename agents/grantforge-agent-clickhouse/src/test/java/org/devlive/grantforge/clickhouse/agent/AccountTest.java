// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest
{
    @Test
    void tellsTheDefaultRoles()
    {
        assertThat(new Account("a", Set.of(), Set.of(), true, List.of(), List.of()).isDefault("r")).isTrue();
        assertThat(new Account("a", Set.of(), Set.of(), true, List.of(), List.of("r")).isDefault("r")).isFalse();
        assertThat(new Account("a", Set.of(), Set.of(), false, List.of("r"), List.of()).isDefault("r")).isTrue();
        assertThat(new Account("a", Set.of(), Set.of(), false, List.of("s"), List.of()).isDefault("r")).isFalse();
    }

    @Test
    void copiesTheRoles()
    {
        List<String> defaults = new ArrayList<>(List.of("r"));
        Account account = new Account("a", Set.of("g"), Set.of("r"), false, defaults, List.of());
        defaults.clear();
        assertThat(account.defaultRoles()).containsExactly("r");
        assertThat(account.groups()).containsExactly("g");
        assertThat(account.granted()).containsExactly("r");
    }
}
