// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeTest
{
    @Test
    void coversRowsFromEverythingDownToConditions()
    {
        assertThat(DataScope.values()).containsExactly(DataScope.ALL, DataScope.TENANT, DataScope.ORG_AND_CHILDREN, DataScope.ORG,
                DataScope.CUSTOM_ORGS, DataScope.SELF, DataScope.CONDITION);
    }
}
