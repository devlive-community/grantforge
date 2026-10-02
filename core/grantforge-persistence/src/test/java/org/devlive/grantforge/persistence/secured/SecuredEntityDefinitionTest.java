// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecuredEntityDefinitionTest
{
    @Test
    void offersTheScopesWhatRowsBelongToAllows()
    {
        SecuredEntityDefinition plain = new SecuredEntityDefinition("group", "Groups", Object.class, List.of(), null, null, false, true, null);
        assertThat(plain.scopes()).containsExactlyInAnyOrder(DataScope.ALL, DataScope.TENANT);
        assertThat(plain.hasUnits()).isFalse();
        SecuredEntityDefinition units = new SecuredEntityDefinition("org-unit", "Departments", Object.class, List.of(), null, "id", false,
                true, null);
        assertThat(units.scopes()).contains(DataScope.ORG, DataScope.ORG_AND_CHILDREN, DataScope.CUSTOM_ORGS).doesNotContain(DataScope.SELF);
    }
}
