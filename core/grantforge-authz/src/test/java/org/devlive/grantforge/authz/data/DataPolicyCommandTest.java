// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataPolicyCommandTest
{
    @Test
    void keepsItsOwnCopyOfTheDepartments()
    {
        List<Long> units = new ArrayList<>(List.of(1L));
        DataPolicyCommand command = new DataPolicyCommand("user", DataAction.READ, DataScope.CUSTOM_ORGS, GrantEffect.ALLOW, null, units);
        units.add(2L);
        assertThat(command.orgUnitIds()).containsExactly(1L);
    }
}
