// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApplicationView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationResponseTest
{
    @Test
    void exposesIdsAsStrings()
    {
        assertThat(ApplicationResponse.from(new ApplicationView(9_007_199_254_740_993L, "crm", "CRM", "Sales", true, 4)))
                .isEqualTo(new ApplicationResponse("9007199254740993", "crm", "CRM", "Sales", true, 4));
    }
}
