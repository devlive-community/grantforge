// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SodMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SodConstraintViewTest
{
    @Test
    void copiesTheRoles()
    {
        List<RoleView> roles = new ArrayList<>(List.of(new RoleView(1, "payer", "Payer", null, RoleType.CUSTOM, true)));
        SodConstraintView view = new SodConstraintView(3, "payments", "Payments", null, roles, 1, SodMode.REPORT, true);
        roles.clear();

        assertThat(view.roles()).extracting(RoleView::code).containsExactly("payer");
        assertThat(view.mode()).isEqualTo(SodMode.REPORT);
    }
}
