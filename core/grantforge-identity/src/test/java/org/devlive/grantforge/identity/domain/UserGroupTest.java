// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserGroupTest
{
    @Test
    void normalizesAndValidatesItsDetails()
    {
        UserGroup group = UserGroup.create(" OPS ", " 运维组 ", " 值班 ");

        assertThat(group.getCode()).isEqualTo("ops");
        assertThat(group.getName()).isEqualTo("运维组");
        assertThat(group.getDescription()).isEqualTo("值班");
        group.change("ops.cn", "Ops", " ");
        assertThat(group.getDescription()).isNull();
        assertThatThrownBy(() -> group.change("-x", "Ops", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> group.change("ops", " ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> group.change("ops", "x".repeat(UserGroup.NAME_MAX + 1), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> group.change("ops", "Ops", "x".repeat(UserGroup.DESCRIPTION_MAX + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(group.getCode()).isEqualTo("ops.cn");
    }
}
