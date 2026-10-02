// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest
{
    @Test
    void customRolesNormalizeAndChange()
    {
        Role role = Role.create(" Auditors ", " 审计员 ", " ");

        assertThat(role.getCode()).isEqualTo("auditors");
        assertThat(role.getName()).isEqualTo("审计员");
        assertThat(role.getDescription()).isNull();
        assertThat(role.getType()).isEqualTo(RoleType.CUSTOM);
        assertThat(role.isEnabled()).isTrue();

        role.change("audit.read", "Readers", " read only ");
        role.enable(false);
        assertThat(role.getCode()).isEqualTo("audit.read");
        assertThat(role.getDescription()).isEqualTo("read only");
        assertThat(role.isEnabled()).isFalse();
    }

    @Test
    void refusesInvalidValues()
    {
        assertThatThrownBy(() -> Role.create("bad code", "X", null)).hasMessageContaining("code");
        assertThatThrownBy(() -> Role.create("x", " ", null)).hasMessageContaining("name");
        assertThatThrownBy(() -> Role.create("x", "n".repeat(Role.NAME_MAX + 1), null)).hasMessageContaining("name");
        assertThatThrownBy(() -> Role.create("x", "X", "d".repeat(Role.DESCRIPTION_MAX + 1))).hasMessageContaining("description");
    }

    @Test
    void systemRolesCannotChange()
    {
        Role admin = Role.system(SystemRole.TENANT_ADMIN);

        assertThat(admin.getType()).isEqualTo(RoleType.SYSTEM);
        assertThat(admin.getCode()).isEqualTo("tenant-admin");
        assertThat(admin.getName()).isEqualTo("Tenant administrator");
        assertThatThrownBy(() -> admin.change("x", "X", null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> admin.enable(false)).isInstanceOf(IllegalStateException.class);
    }
}
