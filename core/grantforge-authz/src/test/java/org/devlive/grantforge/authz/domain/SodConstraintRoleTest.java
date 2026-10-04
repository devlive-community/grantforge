// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SodConstraintRoleTest
{
    @Test
    void linksARoleToAConstraint()
    {
        SodConstraintRole link = SodConstraintRole.of(3, 7);

        assertThat(link.getConstraintId()).isEqualTo(3);
        assertThat(link.getRoleId()).isEqualTo(7);
    }
}
