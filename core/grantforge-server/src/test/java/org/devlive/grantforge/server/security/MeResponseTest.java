// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.AccountProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MeResponseTest
{
    @Test
    void copiesTheProfileWithoutItsId()
    {
        AccountProfile profile = new AccountProfile(9, "alice", "Alice", "a@example.org", "acme", "Acme", true, false,
                Instant.EPOCH, "Corporate LDAP");

        assertThat(MeResponse.from(profile)).isEqualTo(new MeResponse("alice", "Alice", "a@example.org", "acme", "Acme",
                true, false, Instant.EPOCH, "Corporate LDAP"));
    }
}
