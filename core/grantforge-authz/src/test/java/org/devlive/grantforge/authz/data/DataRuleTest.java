// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataRuleTest
{
    @Test
    void keepsItsOwnCopyOfTheDepartments()
    {
        List<Long> units = new ArrayList<>(List.of(1L));
        DataRule rule = new DataRule(DataScope.CUSTOM_ORGS, null, units);
        units.add(2L);
        assertThat(rule.orgUnitIds()).containsExactly(1L);
        assertThat(DataRule.of(DataScope.SELF)).isEqualTo(new DataRule(DataScope.SELF, null, List.of()));
    }
}
