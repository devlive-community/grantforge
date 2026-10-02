// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.RoleInheritanceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleLinkResponseTest
{
    @Test
    void idsBecomeStrings()
    {
        assertThat(RoleLinkResponse.from(new RoleInheritanceService.Link(9_007_199_254_740_993L, 2)))
                .isEqualTo(new RoleLinkResponse("9007199254740993", "2"));
    }
}
