// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessReviewCommandTest
{
    @Test
    void copiesTheRoles()
    {
        Set<Long> roles = new HashSet<>(Set.of(1L, 2L));
        AccessReviewCommand command = new AccessReviewCommand("Quarterly", null, roles, 7, 90, ReviewFallback.KEEP, true, null);
        roles.add(3L);

        assertThat(command.roleIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(command.intervalDays()).isEqualTo(90);
    }
}
