// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestableRoleViewTest
{
    @Test
    void exposesItsComponents()
    {
        RequestableRoleView view = new RequestableRoleView(new RoleView(1, "reports", "Reports", null, RoleType.CUSTOM, true), 30);

        assertThat(view.role().code()).isEqualTo("reports");
        assertThat(view.maxDays()).isEqualTo(30);
    }
}
