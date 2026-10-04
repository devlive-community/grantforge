// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PendingSignInTest
{
    private static final Instant NOW = Instant.parse("2026-06-01T09:00:00Z");

    @Test
    void waitsFiveMinutes()
    {
        MockHttpSession session = new MockHttpSession();
        assertThat(PendingSignIn.current(null, NOW)).isNull();
        assertThat(PendingSignIn.current(session, NOW)).isNull();

        PendingSignIn.start(session, 7, 1, NOW);

        assertThat(PendingSignIn.current(session, NOW.plus(PendingSignIn.TIMEOUT)))
                .isEqualTo(new PendingSignIn(7, 1, NOW.plus(PendingSignIn.TIMEOUT).toEpochMilli()));
        assertThat(PendingSignIn.current(session, NOW.plus(PendingSignIn.TIMEOUT).plusMillis(1))).isNull();
        // A sign-in that took too long is forgotten.
        assertThat(PendingSignIn.current(session, NOW)).isNull();

        PendingSignIn.start(session, 7, 1, NOW);
        PendingSignIn.clear(session);
        assertThat(PendingSignIn.current(session, NOW)).isNull();
    }
}
