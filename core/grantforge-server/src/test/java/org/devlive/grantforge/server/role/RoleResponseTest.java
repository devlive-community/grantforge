// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.domain.RoleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleResponseTest
{
    @Test
    void exposesIdsAsStrings()
    {
        assertThat(RoleResponse.from(new RoleView(9_007_199_254_740_993L, "auditors", "Auditors", "d", RoleType.CUSTOM, false)))
                .isEqualTo(new RoleResponse("9007199254740993", "auditors", "Auditors", "d", RoleType.CUSTOM, false));
    }
}
