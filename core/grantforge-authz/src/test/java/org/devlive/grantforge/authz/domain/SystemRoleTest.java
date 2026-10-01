// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemRoleTest
{
    @Test
    void everyTenantAdministersItselfAndOnlyThePlatformAdministersThePlatform()
    {
        assertThat(SystemRole.TENANT_ADMIN.code()).isEqualTo("tenant-admin");
        assertThat(SystemRole.TENANT_ADMIN.defaultName()).isEqualTo("Tenant administrator");
        assertThat(SystemRole.TENANT_ADMIN.belongsTo(false)).isTrue();
        assertThat(SystemRole.TENANT_ADMIN.belongsTo(true)).isTrue();
        assertThat(SystemRole.PLATFORM_ADMIN.code()).isEqualTo("platform-admin");
        assertThat(SystemRole.PLATFORM_ADMIN.belongsTo(true)).isTrue();
        assertThat(SystemRole.PLATFORM_ADMIN.belongsTo(false)).isFalse();
    }
}
