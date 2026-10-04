// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.audit;

import org.devlive.grantforge.audit.application.AuditEventView;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventResponseTest
{
    @Test
    void writesIdsAsText()
    {
        Instant at = Instant.parse("2026-10-01T08:00:00Z");
        assertThat(AuditEventResponse.from(new AuditEventView(9_007_199_254_740_993L, at, AuditAction.LOGOUT, AuditOutcome.SUCCESS, 1L, null,
                "alice", "t", null, "ip", "ua", "req"))).isEqualTo(new AuditEventResponse("9007199254740993", at, AuditAction.LOGOUT,
                AuditOutcome.SUCCESS, "1", null, "alice", "t", null, "ip", "ua", "req"));
    }
}
