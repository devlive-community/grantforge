// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrgUnitTest
{
    @Test
    void pathsListTheAncestorsAndTheUnitItself()
    {
        OrgUnit root = OrgUnit.create(null, " HQ ", " 总部 ", 0);
        OrgUnit sales = OrgUnit.create(root, "sales", "Sales", 3);

        assertThat(root.getPath()).isEqualTo("/" + root.requireId() + "/");
        assertThat(root.getCode()).isEqualTo("hq");
        assertThat(root.getName()).isEqualTo("总部");
        assertThat(root.getParentId()).isNull();
        assertThat(sales.getPath()).isEqualTo(root.getPath() + sales.requireId() + "/");
        assertThat(sales.getDepth()).isOne();
        assertThat(sales.getParentId()).isEqualTo(root.requireId());
        assertThat(sales.getSortOrder()).isEqualTo(3);
        assertThat(root.contains(sales)).isTrue();
        assertThat(root.contains(root)).isTrue();
        assertThat(sales.contains(root)).isFalse();
    }

    @Test
    void nestsAtMostTheMaximumDepth()
    {
        OrgUnit unit = OrgUnit.create(null, "l0", "Level 0", 0);
        for (int level = 1; level <= OrgUnit.MAX_DEPTH; level++) {
            unit = OrgUnit.create(unit, "l" + level, "Level " + level, 0);
        }
        OrgUnit deepest = unit;

        assertThat(deepest.getDepth()).isEqualTo(OrgUnit.MAX_DEPTH);
        // 16 levels of 19-digit IDs still fit the 400-character path column.
        assertThat(deepest.getPath()).hasSizeLessThanOrEqualTo(400);
        assertThatThrownBy(() -> OrgUnit.create(deepest, "too-deep", "Too deep", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesCodesAndNames()
    {
        OrgUnit unit = OrgUnit.create(null, "hq", "HQ", 0);

        unit.rename("hq.cn_1-a", "HQ China");
        unit.placeAt(7);
        assertThat(unit.getCode()).isEqualTo("hq.cn_1-a");
        assertThat(unit.getSortOrder()).isEqualTo(7);
        assertThatThrownBy(() -> unit.rename("-x", "HQ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unit.rename("a b", "HQ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unit.rename("x".repeat(65), "HQ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unit.rename("hq", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> unit.rename("hq", "x".repeat(OrgUnit.NAME_MAX + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(unit.getName()).isEqualTo("HQ China");
    }
}
