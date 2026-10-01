// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectiveRoleTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsSources()
    {
        RoleView role = new RoleView(1, "auditors", "Auditors", null, RoleType.CUSTOM, true);
        List<AssignmentView> sources = new ArrayList<>(List.of(new AssignmentView(3, 1, new Subject(SubjectType.USER, 2, "A", "a"),
                RoleAssignment.Terms.UNLIMITED, true)));
        EffectiveRole effective = new EffectiveRole(role, sources, true);
        sources.clear();

        assertThat(effective.sources()).hasSize(1);
        assertThatThrownBy(() -> new EffectiveRole(null, List.of(), true)).isInstanceOf(NullPointerException.class);
    }
}
