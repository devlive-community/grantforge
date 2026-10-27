// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest
{
    @Test
    void copiesItsGroups()
    {
        Set<String> groups = new HashSet<>(Set.of("analysts"));
        Account account = new Account("alice", groups, true);
        groups.add("admins");
        assertThat(account.groups()).containsExactly("analysts");
        assertThat(account.internal()).isTrue();
        assertThat(account.name()).isEqualTo("alice");
    }
}
