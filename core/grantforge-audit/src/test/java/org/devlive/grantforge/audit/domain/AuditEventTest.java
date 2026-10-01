// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void keepsEveryValue()
    {
        AuditEvent event = AuditEvent.of(NOW, AuditAction.SESSION_REVOKED, AuditOutcome.SUCCESS, 1L, 2L, "admin", "3",
                "ADMIN", "10.0.0.1", "Firefox", "req-1");

        assertThat(event).extracting(AuditEvent::getOccurredAt, AuditEvent::getAction, AuditEvent::getOutcome,
                AuditEvent::getTenantId, AuditEvent::getActorId, AuditEvent::getActorName, AuditEvent::getTargetId,
                AuditEvent::getReason, AuditEvent::getClientIp, AuditEvent::getUserAgent, AuditEvent::getRequestId)
                .containsExactly(NOW, AuditAction.SESSION_REVOKED, AuditOutcome.SUCCESS, 1L, 2L, "admin", "3", "ADMIN",
                        "10.0.0.1", "Firefox", "req-1");
    }

    @Test
    void trimsCutsAndDropsTextToFitTheColumns()
    {
        AuditEvent event = AuditEvent.of(NOW, AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, null, null,
                " " + "n".repeat(70) + " ", " ", null, "x".repeat(50), "a".repeat(300), "r".repeat(70));

        assertThat(event.getActorName()).hasSize(AuditEvent.MAX_ACTOR_NAME);
        assertThat(event.getTargetId()).isNull();
        assertThat(event.getReason()).isNull();
        assertThat(event.getClientIp()).hasSize(AuditEvent.MAX_CLIENT_IP);
        assertThat(event.getUserAgent()).hasSize(AuditEvent.MAX_USER_AGENT);
        assertThat(event.getRequestId()).hasSize(AuditEvent.MAX_CODE);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresTimeActionAndOutcome()
    {
        assertThatThrownBy(() -> AuditEvent.of(null, AuditAction.LOGOUT, AuditOutcome.SUCCESS, null, null, null, null,
                null, null, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditEvent.of(NOW, null, AuditOutcome.SUCCESS, null, null, null, null, null, null,
                null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditEvent.of(NOW, AuditAction.LOGOUT, null, null, null, null, null, null, null, null,
                null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(new AuditEvent()::getOccurredAt).isInstanceOf(NullPointerException.class);
    }
}
