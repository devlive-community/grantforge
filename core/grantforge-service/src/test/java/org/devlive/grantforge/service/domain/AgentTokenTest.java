// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTokenTest
{
    @Test
    void worksUntilRevokedOrExpired()
    {
        Instant now = Instant.parse("2026-10-02T12:00:00Z");
        AgentToken token = AgentToken.create(3, "cluster", "hash", "gfa_abcdef", now.plusSeconds(60));
        assertThat(token).extracting(AgentToken::getServiceId, AgentToken::getName, AgentToken::getTokenHint, AgentToken::getExpiresAt)
                .containsExactly(3L, "cluster", "gfa_abcdef", now.plusSeconds(60));
        assertThat(token.isUsableAt(now)).isTrue();
        assertThat(token.isUsableAt(now.plusSeconds(60))).isFalse();
        token.used(now);
        assertThat(token.getLastUsedAt()).isEqualTo(now);
        token.revoke(now);
        token.revoke(now.plusSeconds(5));
        assertThat(token.getRevokedAt()).isEqualTo(now);
        assertThat(token.isUsableAt(now)).isFalse();
        assertThat(AgentToken.create(3, "forever", "hash2", "gfa_x", null).isUsableAt(Instant.MAX)).isTrue();
    }
}
