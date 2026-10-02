// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceTypeTest
{
    @Test
    void namesFitTheTypeColumn()
    {
        // The resource_type column is VARCHAR(16).
        assertThat(Arrays.stream(ResourceType.values()).map(Enum::name)).allSatisfy(name -> assertThat(name).hasSizeLessThanOrEqualTo(16));
    }

    @Test
    void placementFollowsTheConsoleStructure()
    {
        assertThat(ResourceType.MODULE.childTypes()).containsExactly(ResourceType.MODULE, ResourceType.MENU, ResourceType.PAGE,
                ResourceType.API, ResourceType.DATA_ENTITY);
        assertThat(ResourceType.MENU.childTypes()).containsExactly(ResourceType.MENU, ResourceType.PAGE);
        assertThat(ResourceType.PAGE.childTypes()).containsExactly(ResourceType.TAB, ResourceType.ACTION);
        assertThat(ResourceType.TAB.childTypes()).containsExactly(ResourceType.TAB, ResourceType.ACTION);
        assertThat(ResourceType.DATA_ENTITY.childTypes()).containsExactly(ResourceType.FIELD);
        assertThat(ResourceType.ACTION.childTypes()).isEmpty();
        assertThat(ResourceType.API.childTypes()).isEmpty();
        assertThat(ResourceType.FIELD.childTypes()).isEmpty();

        assertThat(Arrays.stream(ResourceType.values()).filter(type -> type.allowsParent(null)))
                .containsExactly(ResourceType.MODULE, ResourceType.MENU, ResourceType.PAGE, ResourceType.API, ResourceType.DATA_ENTITY);
    }
}
