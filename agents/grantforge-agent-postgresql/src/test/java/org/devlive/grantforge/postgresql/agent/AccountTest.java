// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest
{
    @Test
    void copiesItsRoles()
    {
        Set<String> groups = new HashSet<>(Set.of("analysts"));
        Account account = new Account("carol", groups, Set.of("analysts"), Set.of("analysts"));
        groups.add("admins");
        assertThat(account.groups()).containsExactly("analysts");
        assertThatThrownBy(() -> account.granted().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(account.inheriting()).containsExactly("analysts");
    }
}
