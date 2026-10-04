// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewItemViewTest
{
    @Test
    void exposesItsComponents()
    {
        ReviewItemView view = new ReviewItemView(9, new RoleView(1, "reports", "Reports", null, RoleType.CUSTOM, true),
                new Subject(SubjectType.GROUP, 8, "Developers", "dev"), true, null, null, false, ReviewDecision.PENDING, null, null, null,
                null);

        assertThat(view.subject().name()).isEqualTo("Developers");
        assertThat(view.assigned()).isTrue();
        assertThat(view.outcome()).isNull();
    }
}
