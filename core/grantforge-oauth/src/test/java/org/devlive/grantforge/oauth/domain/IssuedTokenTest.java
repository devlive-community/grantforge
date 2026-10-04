// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IssuedTokenTest
{
    @Test
    void holdsATokenOrNothing()
    {
        IssuedToken token = new IssuedToken("h", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), true);

        assertThat(token.isPresent()).isTrue();
        assertThat(token.getHash()).isEqualTo("h");
        assertThat(token.getIssuedAt()).isEqualTo(Instant.EPOCH);
        assertThat(token.getExpiresAt()).isEqualTo(Instant.EPOCH.plusSeconds(1));
        assertThat(token.isInvalidated()).isTrue();
        assertThat(IssuedToken.none().isPresent()).isFalse();
        assertThat(IssuedToken.none().isInvalidated()).isFalse();
        assertThat(token).isEqualTo(new IssuedToken("h", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), true))
                .hasSameHashCodeAs(new IssuedToken("h", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), true))
                .isNotEqualTo(new IssuedToken("h", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), false))
                .isNotEqualTo(IssuedToken.none());
        assertThat(token.toString()).doesNotContain("h,").contains("invalidated=true");
        assertThat(IssuedToken.none()).hasToString("IssuedToken[none]").isEqualTo(IssuedToken.none());
    }
}
