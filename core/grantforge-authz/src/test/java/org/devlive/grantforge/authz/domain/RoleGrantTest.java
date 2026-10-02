// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleGrantTest
{
    private static final Instant END = Instant.parse("2026-12-31T00:00:00Z");

    @Test
    void grantsUiResourcesAndApisUntilTheyExpire()
    {
        Resource page = Resource.create(1, null, ResourceType.PAGE, "users", CatalogTestData.details("Users"), 0);
        RoleGrant grant = RoleGrant.create(9, page, GrantEffect.ALLOW, END, 3);

        assertThat(grant.getRoleId()).isEqualTo(9);
        assertThat(grant.getResourceId()).isEqualTo(page.requireId());
        assertThat(grant.getEffect()).isEqualTo(GrantEffect.ALLOW);
        assertThat(grant.getExpiresAt()).isEqualTo(END);
        assertThat(grant.getGrantedBy()).isEqualTo(3);
        assertThat(grant.appliesAt(END.minusSeconds(1))).isTrue();
        assertThat(grant.appliesAt(END)).isFalse();

        grant.change(GrantEffect.DENY, null, 4);
        assertThat(grant.appliesAt(Instant.MAX)).isTrue();
        RoleGrant copy = grant.copyTo(10, 5);
        assertThat(copy.getRoleId()).isEqualTo(10);
        assertThat(copy.getEffect()).isEqualTo(GrantEffect.DENY);
        assertThat(copy.getGrantedBy()).isEqualTo(5);
        assertThat(copy.getResourceId()).isEqualTo(page.requireId());
    }

    @Test
    void modulesDataEntitiesAndFieldsCannotBeGranted()
    {
        Resource module = Resource.create(1, null, ResourceType.MODULE, "system", CatalogTestData.details("S"), 0);

        assertThatThrownBy(() -> RoleGrant.create(9, module, GrantEffect.ALLOW, null, 3)).hasMessageContaining("MODULE");
        assertThat(RoleGrant.GRANTABLE).doesNotContain(ResourceType.MODULE, ResourceType.DATA_ENTITY, ResourceType.FIELD);
    }
}
