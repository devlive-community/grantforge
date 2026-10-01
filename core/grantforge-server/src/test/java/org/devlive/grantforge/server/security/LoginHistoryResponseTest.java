// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.audit.application.AuditEntry;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginHistoryResponseTest
{
    @Test
    void copiesTheEntry()
    {
        Instant now = Instant.parse("2026-10-01T08:00:00Z");

        assertThat(LoginHistoryResponse.from(new AuditEntry(now, AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE,
                "GF-IDENTITY-020", "10.0.0.1", "Firefox"))).isEqualTo(new LoginHistoryResponse(now,
                AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, "GF-IDENTITY-020", "10.0.0.1", "Firefox"));
    }
}
