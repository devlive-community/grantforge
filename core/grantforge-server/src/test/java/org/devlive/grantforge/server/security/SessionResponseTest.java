// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.ActiveSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SessionResponseTest
{
    @Test
    void exposesTheHandleAsAStringAndHidesTheAccountId()
    {
        Instant signedIn = Instant.parse("2026-10-01T08:00:00Z");
        Instant seen = signedIn.plusSeconds(60);
        ActiveSession session = new ActiveSession(9_007_199_254_740_993L, 7, "alice", "Alice", "10.0.0.1", "Firefox",
                signedIn, seen, true);

        assertThat(SessionResponse.from(session)).isEqualTo(new SessionResponse("9007199254740993", "alice", "Alice",
                "10.0.0.1", "Firefox", signedIn, seen, true));
    }
}
