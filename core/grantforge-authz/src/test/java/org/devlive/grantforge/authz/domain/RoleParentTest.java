// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleParentTest
{
    @Test
    void linksTwoDifferentRoles()
    {
        RoleParent link = RoleParent.of(1, 2);
        assertThat(link.getRoleId()).isEqualTo(1);
        assertThat(link.getParentId()).isEqualTo(2);
        assertThatThrownBy(() -> RoleParent.of(3, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
