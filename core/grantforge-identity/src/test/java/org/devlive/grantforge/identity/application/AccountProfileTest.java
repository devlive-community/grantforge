// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountProfileTest
{
    @Test
    void exposesItsComponents()
    {
        AccountProfile profile = new AccountProfile(1, "alice", "Alice", "a@example.org", "acme", "Acme", false, false,
                Instant.EPOCH, "Corporate LDAP");

        assertThat(profile.email()).isEqualTo("a@example.org");
        assertThat(profile.identitySource()).isEqualTo("Corporate LDAP");
        assertThat(profile.tenantName()).isEqualTo("Acme");
        assertThat(profile.lastLoginAt()).isEqualTo(Instant.EPOCH);
    }
}
