// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTypeTest
{
    @Test
    void namesFitTheTypeColumnAndSortSystemRolesFirst()
    {
        // Stored by name in the VARCHAR(16) role_type column; lists order by it descending.
        assertThat(RoleType.values()).extracting(Enum::name).containsExactly("SYSTEM", "CUSTOM");
        assertThat("SYSTEM").isGreaterThan("CUSTOM");
    }
}
