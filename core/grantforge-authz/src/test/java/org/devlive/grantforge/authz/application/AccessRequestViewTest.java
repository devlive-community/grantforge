// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.AccessRequestStatus;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccessRequestViewTest
{
    @Test
    void exposesItsComponents()
    {
        AccessRequestView view = new AccessRequestView(5, new Subject(SubjectType.USER, 7, "Alice", "alice"),
                new RoleView(1, "reports", "Reports", null, RoleType.CUSTOM, true), "why", 3, AccessRequestStatus.PENDING, Instant.EPOCH, null, null,
                null, null, null);

        assertThat(view.requester().name()).isEqualTo("Alice");
        assertThat(view.status()).isEqualTo(AccessRequestStatus.PENDING);
        assertThat(view.decidedBy()).isNull();
    }
}
