// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRowTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresTheNameStatusAndCreationTime()
    {
        Instant now = Instant.EPOCH;

        assertThat(new UserRow(1, "alice", null, null, AccountStatus.ACTIVE, null, false, false, null, now, null, null)
                .username()).isEqualTo("alice");
        assertThatThrownBy(() -> new UserRow(1, null, null, null, AccountStatus.ACTIVE, null, false, false, null, now,
                null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserRow(1, "a", null, null, null, null, false, false, null, now, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserRow(1, "a", null, null, AccountStatus.ACTIVE, null, false, false, null, null,
                null, null)).isInstanceOf(NullPointerException.class);
    }
}
