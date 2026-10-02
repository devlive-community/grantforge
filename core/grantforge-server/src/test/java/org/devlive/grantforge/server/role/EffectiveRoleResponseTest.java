// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.AssignmentView;
import org.devlive.grantforge.authz.application.EffectiveRole;
import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveRoleResponseTest
{
    @Test
    void convertsTheRoleAndItsSources()
    {
        AssignmentView source = new AssignmentView(5, 1, new Subject(SubjectType.GROUP, 4, "Dev", "dev"), RoleAssignment.Terms.UNLIMITED,
                true);
        EffectiveRoleResponse response = EffectiveRoleResponse.from(new EffectiveRole(new RoleView(1, "auditors", "Auditors", null,
                RoleType.CUSTOM, true), List.of(source), true));

        assertThat(response.role().code()).isEqualTo("auditors");
        assertThat(response.sources()).containsExactly(AssignmentResponse.from(source));
        assertThat(response.active()).isTrue();
    }
}
