// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleInheritanceTest
{
    private static final RoleView ROLE = new RoleView(1, "auditors", "Auditors", null, RoleType.CUSTOM, true);

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsListsAndNeedsItsValues()
    {
        List<RoleView> parents = new ArrayList<>(List.of(ROLE));
        RoleInheritance inheritance = new RoleInheritance(ROLE, parents, List.of(new RoleInheritance.Related(ROLE, 1)), List.of());
        parents.clear();

        assertThat(inheritance.parents()).containsExactly(ROLE);
        assertThat(inheritance.ancestors()).extracting(RoleInheritance.Related::distance).containsExactly(1);
        assertThatThrownBy(() -> new RoleInheritance(null, List.of(), List.of(), List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoleInheritance.Related(null, 1)).isInstanceOf(NullPointerException.class);
    }
}
