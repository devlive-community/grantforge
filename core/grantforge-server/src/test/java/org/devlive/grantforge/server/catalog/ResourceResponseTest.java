// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ResourceView;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceResponseTest
{
    @Test
    void flattensTheSettingsAndExposesIdsAsStrings()
    {
        ResourceView view = new ResourceView(9_007_199_254_740_993L, 2, 3L, ResourceType.PAGE, "users",
                new ResourceDetails("Users", "List", "/admin/users", false, true, DenyMode.DISABLE), 4, 1, true);

        assertThat(ResourceResponse.from(view)).isEqualTo(new ResourceResponse("9007199254740993", "2", "3", ResourceType.PAGE,
                "users", "Users", "List", "/admin/users", 4, 1, false, true, DenyMode.DISABLE, true));
        assertThat(ResourceResponse.from(new ResourceView(1, 2, null, ResourceType.MODULE, "m",
                new ResourceDetails("M", null, null, true, true, DenyMode.HIDE), 0, 0, false)).parentId()).isNull();
    }
}
