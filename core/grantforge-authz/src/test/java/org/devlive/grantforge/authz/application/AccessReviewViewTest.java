// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.devlive.grantforge.authz.domain.RoleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessReviewViewTest
{
    @Test
    void copiesTheRoles()
    {
        List<RoleView> roles = new ArrayList<>(List.of(new RoleView(1, "reports", "Reports", null, RoleType.CUSTOM, true)));
        AccessReviewView view = new AccessReviewView(4, "Quarterly", null, roles, 7, null, ReviewFallback.KEEP, true, null, null, null);
        roles.clear();

        assertThat(view.roles()).extracting(RoleView::code).containsExactly("reports");
        assertThat(view.openRound()).isNull();
    }
}
