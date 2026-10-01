// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveSessionTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresTheOwnerAndTimes()
    {
        Instant now = Instant.EPOCH;

        assertThat(new ActiveSession(1, 2, "alice", null, null, null, now, now, true).current()).isTrue();
        assertThatThrownBy(() -> new ActiveSession(1, 2, null, null, null, null, now, now, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActiveSession(1, 2, "alice", null, null, null, null, now, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ActiveSession(1, 2, "alice", null, null, null, now, null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
