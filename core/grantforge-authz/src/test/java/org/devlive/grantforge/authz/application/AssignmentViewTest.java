// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentViewTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresSubjectAndTerms()
    {
        Subject dev = new Subject(SubjectType.GROUP, 1, "Dev", "dev");

        assertThat(new AssignmentView(1, 2, dev, RoleAssignment.Terms.UNLIMITED, true).valid()).isTrue();
        assertThatThrownBy(() -> new AssignmentView(1, 2, null, RoleAssignment.Terms.UNLIMITED, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AssignmentView(1, 2, dev, null, true)).isInstanceOf(NullPointerException.class);
    }
}
