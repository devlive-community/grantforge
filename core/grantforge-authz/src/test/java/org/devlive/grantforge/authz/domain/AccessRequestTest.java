// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessRequestTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void movesFromPendingToADecisionAndAnEnd()
    {
        AccessRequest request = AccessRequest.file(7, 3, "Reports", 10);
        assertThat(request).extracting(AccessRequest::getRequesterId, AccessRequest::getRoleId, AccessRequest::getReason, AccessRequest::getRequestedDays,
                AccessRequest::getStatus).containsExactly(7L, 3L, "Reports", 10, AccessRequestStatus.PENDING);

        request.approve(9, NOW, "ok", 42, NOW.plusSeconds(60));
        assertThat(request).extracting(AccessRequest::getStatus, AccessRequest::getDecidedBy, AccessRequest::getDecidedAt, AccessRequest::getDecisionComment,
                AccessRequest::getAssignmentId, AccessRequest::getValidUntil)
                .containsExactly(AccessRequestStatus.APPROVED, 9L, NOW, "ok", 42L, NOW.plusSeconds(60));
        assertThatThrownBy(() -> request.reject(9, NOW, null)).isInstanceOf(IllegalStateException.class);
        request.end(NOW.plusSeconds(60), false);
        assertThat(request.getStatus()).isEqualTo(AccessRequestStatus.EXPIRED);
        assertThat(request.getEndedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> request.end(NOW, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void endsWithoutAGrantToo()
    {
        AccessRequest rejected = AccessRequest.file(7, 3, "Reports", 1);
        rejected.reject(9, NOW, "no");
        assertThat(rejected.getStatus()).isEqualTo(AccessRequestStatus.REJECTED);
        AccessRequest cancelled = AccessRequest.file(7, 3, "Reports", 1);
        cancelled.cancel(NOW);
        assertThat(cancelled.getStatus()).isEqualTo(AccessRequestStatus.CANCELLED);
        AccessRequest revoked = AccessRequest.file(7, 3, "Reports", 1);
        revoked.approve(9, NOW, null, 1, NOW);
        revoked.end(NOW, true);
        assertThat(revoked.getStatus()).isEqualTo(AccessRequestStatus.REVOKED);
        assertThatThrownBy(() -> AccessRequest.file(7, 3, "Reports", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
