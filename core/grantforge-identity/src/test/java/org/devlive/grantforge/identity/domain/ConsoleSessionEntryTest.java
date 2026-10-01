// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleSessionEntryTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresASessionAndAUsername()
    {
        ConsoleSession session = ConsoleSession.start("sid", 1, null, null, Instant.EPOCH);

        assertThat(new ConsoleSessionEntry(session, "alice", null).username()).isEqualTo("alice");
        assertThatThrownBy(() -> new ConsoleSessionEntry(null, "alice", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConsoleSessionEntry(session, null, null)).isInstanceOf(NullPointerException.class);
    }
}
