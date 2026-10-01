// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleSessionTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void startsSeenAtSignIn()
    {
        ConsoleSession session = ConsoleSession.start("sid", 7, "10.0.0.1", "Firefox", NOW);

        assertThat(session.getSessionId()).isEqualTo("sid");
        assertThat(session.getAccountId()).isEqualTo(7);
        assertThat(session.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(session.getUserAgent()).isEqualTo("Firefox");
        assertThat(session.getSignedInAt()).isEqualTo(NOW);
        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
    }

    @Test
    void cutsLongClientDetailsAndDropsBlankOnes()
    {
        ConsoleSession session = ConsoleSession.start("sid", 7, " ", "x".repeat(300), NOW);

        assertThat(session.getClientIp()).isNull();
        assertThat(session.getUserAgent()).hasSize(ConsoleSession.MAX_USER_AGENT);
        assertThat(ConsoleSession.start("sid", 7, null, null, NOW).getUserAgent()).isNull();
    }

    @Test
    void requiresASessionId()
    {
        assertThatThrownBy(() -> ConsoleSession.start(" ", 7, null, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsavedEntriesHaveNoTimes()
    {
        ConsoleSession blank = new ConsoleSession();

        assertThatThrownBy(blank::getSignedInAt).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(blank::getLastSeenAt).isInstanceOf(NullPointerException.class);
    }
}
