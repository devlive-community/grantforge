// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.org;

import org.devlive.grantforge.identity.application.OrgUnitView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrgUnitResponseTest
{
    @Test
    void exposesIdsAsStrings()
    {
        assertThat(OrgUnitResponse.from(new OrgUnitView(9_007_199_254_740_993L, 7L, "sales", "Sales", 2, 1)))
                .isEqualTo(new OrgUnitResponse("9007199254740993", "7", "sales", "Sales", 2, 1));
        assertThat(OrgUnitResponse.from(new OrgUnitView(1, null, "hq", "HQ", 0, 0)).parentId()).isNull();
    }
}
