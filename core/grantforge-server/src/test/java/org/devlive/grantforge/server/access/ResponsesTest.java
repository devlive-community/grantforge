// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import org.devlive.grantforge.authz.application.AccessRequestView;
import org.devlive.grantforge.authz.application.RequestOption;
import org.devlive.grantforge.authz.application.RequestableRoleView;
import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.AccessRequestStatus;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsesTest
{
    private static final RoleView REPORTS = new RoleView(3, "reports", "Reports", null, RoleType.CUSTOM, true);

    @Test
    void convertsTheViews()
    {
        assertThat(RequestableRoleResponse.from(new RequestableRoleView(REPORTS, 30))).extracting(response -> response.role().id(),
                RequestableRoleResponse::maxDays).containsExactly("3", 30);
        assertThat(RequestOptionResponse.from(new RequestOption(REPORTS, 30, false, true)).pending()).isTrue();
        AccessRequestResponse request = AccessRequestResponse.from(new AccessRequestView(5, new Subject(SubjectType.USER, 7, "Alice", "alice"), REPORTS,
                "why", 3, AccessRequestStatus.APPROVED, Instant.EPOCH, new Subject(SubjectType.USER, 9, "Boss", "boss"), Instant.EPOCH, "ok",
                Instant.EPOCH, null));
        assertThat(request).extracting(AccessRequestResponse::id, AccessRequestResponse::requesterId, AccessRequestResponse::requesterUsername,
                AccessRequestResponse::decidedByName).containsExactly("5", "7", "alice", "Boss");
        AccessRequestResponse pending = AccessRequestResponse.from(new AccessRequestView(6, new Subject(SubjectType.USER, 7, "Alice", null), REPORTS,
                "why", 3, AccessRequestStatus.PENDING, Instant.EPOCH, null, null, null, null, null));
        assertThat(pending.decidedByName()).isNull();
    }
}
