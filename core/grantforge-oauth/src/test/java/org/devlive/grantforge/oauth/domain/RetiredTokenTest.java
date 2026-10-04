// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetiredTokenTest
{
    @Test
    void remembersTheTokenAndItsAuthorization()
    {
        RetiredToken token = RetiredToken.of("h", "auth-1", Instant.EPOCH);

        assertThat(token.getTokenHash()).isEqualTo("h");
        assertThat(token.getAuthorizationId()).isEqualTo("auth-1");
        assertThat(token.getExpiresAt()).isEqualTo(Instant.EPOCH);
    }
}
