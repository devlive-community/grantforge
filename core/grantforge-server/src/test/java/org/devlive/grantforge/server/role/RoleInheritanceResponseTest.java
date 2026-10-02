// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.RoleInheritance;
import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.domain.RoleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleInheritanceResponseTest
{
    @Test
    void convertsEveryRoleAndKeepsTheDistances()
    {
        RoleView role = new RoleView(1, "auditors", "Auditors", null, RoleType.CUSTOM, true);
        RoleView parent = new RoleView(2, "editors", "Editors", null, RoleType.CUSTOM, true);
        RoleView child = new RoleView(3, "base", "Base", null, RoleType.CUSTOM, false);

        RoleInheritanceResponse response = RoleInheritanceResponse.from(new RoleInheritance(role, List.of(parent),
                List.of(new RoleInheritance.Related(parent, 1)), List.of(new RoleInheritance.Related(child, 2))));

        assertThat(response.role().id()).isEqualTo("1");
        assertThat(response.parents()).extracting(RoleResponse::code).containsExactly("editors");
        assertThat(response.ancestors()).containsExactly(new RoleInheritanceResponse.Related(RoleResponse.from(parent), 1));
        assertThat(response.descendants()).containsExactly(new RoleInheritanceResponse.Related(RoleResponse.from(child), 2));
    }
}
