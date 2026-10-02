// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoleHierarchyTest
{
    // 4 inherits from 3 and 2; 3 and 2 both inherit from 1 (a diamond); 5 stands alone.
    private final RoleHierarchy hierarchy = new RoleHierarchy(List.of(RoleParent.of(4, 3), RoleParent.of(4, 2),
            RoleParent.of(3, 1), RoleParent.of(2, 1)));

    @Test
    void walksAncestorsAndDescendantsNearestFirstAndOnce()
    {
        assertThat(hierarchy.parentsOf(4)).containsExactly(3L, 2L);
        assertThat(hierarchy.parentsOf(5)).isEmpty();
        assertThat(hierarchy.ancestors(4)).containsExactly(Map.entry(3L, 1), Map.entry(2L, 1), Map.entry(1L, 2));
        assertThat(hierarchy.descendants(1)).containsExactly(Map.entry(3L, 1), Map.entry(2L, 1), Map.entry(4L, 2));
        assertThat(hierarchy.ancestors(1)).isEmpty();
    }

    @Test
    void refusesLinksThatCloseACycle()
    {
        assertThat(hierarchy.wouldCycle(1, 4)).isTrue();
        assertThat(hierarchy.wouldCycle(2, 4)).isTrue();
        assertThat(hierarchy.wouldCycle(5, 5)).isTrue();
        assertThat(hierarchy.wouldCycle(5, 4)).isFalse();
        assertThat(hierarchy.wouldCycle(4, 1)).isFalse();
    }
}
