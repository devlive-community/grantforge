// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.SodMode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SodConstraintCommandTest
{
    @Test
    void copiesTheRoles()
    {
        Set<Long> roles = new HashSet<>(Set.of(1L, 2L));
        SodConstraintCommand command = new SodConstraintCommand("payments", "Payments", null, roles, 1, SodMode.ENFORCE, true);
        roles.clear();

        assertThat(command.roleIds()).containsExactlyInAnyOrder(1L, 2L);
    }
}
