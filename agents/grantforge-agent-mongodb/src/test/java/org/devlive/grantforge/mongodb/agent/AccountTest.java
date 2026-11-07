// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTest
{
    @Test
    void holdsTheIdentitiesOfOneName()
    {
        Account.RoleRef role = new Account.RoleRef("readWrite", "sales");
        Account.Identity identity = new Account.Identity("alice", "admin", List.of(role));
        Account account = new Account("alice", List.of(identity), Set.of("readWrite", "analysts"), Set.of(role));
        assertThat(account.groupNames()).containsExactly("analysts", "readWrite");
        assertThat(identity.label()).isEqualTo("alice@admin");
        assertThat(role.label()).isEqualTo("readWrite@sales");
        assertThat(account.identities()).containsExactly(identity);
        assertThat(identity.roles()).containsExactly(role);
    }
}
