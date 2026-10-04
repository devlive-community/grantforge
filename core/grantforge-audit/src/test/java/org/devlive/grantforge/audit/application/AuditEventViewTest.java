// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventViewTest
{
    @Test
    void copiesAnEvent()
    {
        Instant at = Instant.parse("2026-10-01T08:00:00Z");
        AuditEvent event = AuditEvent.of(at, AuditAction.LOGOUT, AuditOutcome.SUCCESS, 1L, 7L, "alice", "t", "r", "ip", "ua", "req");
        ReflectionTestUtils.setField(event, "id", 42L);
        assertThat(AuditEventView.from(event)).isEqualTo(new AuditEventView(42, at, AuditAction.LOGOUT, AuditOutcome.SUCCESS, 1L, 7L, "alice",
                "t", "r", "ip", "ua", "req"));
    }
}
