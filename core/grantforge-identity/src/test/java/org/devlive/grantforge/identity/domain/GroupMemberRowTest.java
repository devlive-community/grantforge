// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupMemberRowTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresTheNameAndJoinTime()
    {
        assertThat(new GroupMemberRow(1, "alice", null, null, Instant.EPOCH).username()).isEqualTo("alice");
        assertThatThrownBy(() -> new GroupMemberRow(1, null, null, null, Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupMemberRow(1, "alice", null, null, null)).isInstanceOf(NullPointerException.class);
    }
}
