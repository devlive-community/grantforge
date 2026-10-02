// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceViewTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesTheResource()
    {
        Resource page = Resource.create(3, null, ResourceType.PAGE, "page", CatalogTestData.details("Page"), 2);

        assertThat(ResourceView.from(page)).isEqualTo(new ResourceView(page.requireId(), 3, null, ResourceType.PAGE, "page",
                CatalogTestData.details("Page"), 2, 0, false, null));
        assertThatThrownBy(() -> new ResourceView(1, 3, null, null, "page", CatalogTestData.details("Page"), 0, 0, false, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResourceView(1, 3, null, ResourceType.PAGE, null, CatalogTestData.details("Page"), 0, 0,
                false, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResourceView(1, 3, null, ResourceType.PAGE, "page", null, 0, 0, false, null))
                .isInstanceOf(NullPointerException.class);
    }
}
