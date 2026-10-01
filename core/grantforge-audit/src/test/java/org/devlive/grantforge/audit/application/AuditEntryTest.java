// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEntryTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void showsWhatTheUserMaySeeOfAnEvent()
    {
        Instant now = Instant.parse("2026-10-01T08:00:00Z");
        AuditEvent event = AuditEvent.of(now, AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, 1L, 2L, "alice", null,
                "GF-IDENTITY-020", "10.0.0.1", "Firefox", "req");

        assertThat(AuditEntry.from(event)).isEqualTo(new AuditEntry(now, AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE,
                "GF-IDENTITY-020", "10.0.0.1", "Firefox"));
        assertThatThrownBy(() -> new AuditEntry(null, AuditAction.LOGOUT, AuditOutcome.SUCCESS, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEntry(now, null, AuditOutcome.SUCCESS, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuditEntry(now, AuditAction.LOGOUT, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
