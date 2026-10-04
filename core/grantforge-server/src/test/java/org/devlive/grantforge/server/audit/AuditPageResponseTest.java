// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.audit;

import org.devlive.grantforge.audit.application.AuditEventView;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.page.CursorPage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPageResponseTest
{
    @Test
    void convertsTheEventsAndKeepsTheCursor()
    {
        AuditEventView event = new AuditEventView(1, Instant.EPOCH, AuditAction.LOGOUT, AuditOutcome.SUCCESS, null, null, null, null, null,
                null, null, null);
        AuditPageResponse page = AuditPageResponse.from(new CursorPage<>(List.of(event), "abc"));
        assertThat(page.events()).extracting(AuditEventResponse::id).containsExactly("1");
        assertThat(page.next()).isEqualTo("abc");
        assertThat(AuditPageResponse.from(new CursorPage<>(List.of(), null)).next()).isNull();
    }
}
