// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SodMode;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SodConflictTest
{
    @Test
    void namesTheAccountAndTheRoles()
    {
        RoleView payer = new RoleView(1, "payer", "Payer", null, RoleType.CUSTOM, true);
        SodConflict conflict = new SodConflict(new SodConstraintView(3, "payments", "Payments", null, List.of(payer), 1, SodMode.ENFORCE, true),
                new Subject(SubjectType.USER, 7, "Alice", "alice"), List.of(payer));

        assertThat(conflict.account().id()).isEqualTo(7);
        assertThat(conflict.roles()).containsExactly(payer);
    }
}
