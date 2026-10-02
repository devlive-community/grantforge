// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.authz.domain.DataAction;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataPolicyViewTest
{
    @Test
    void keepsItsOwnCopyOfTheDepartments()
    {
        List<Long> units = new ArrayList<>(List.of(1L));
        DataPolicyView view = new DataPolicyView(1, 2, "user", DataAction.READ, DataScope.CUSTOM_ORGS, GrantEffect.ALLOW, null, units,
                Instant.EPOCH);
        units.add(2L);
        assertThat(view.orgUnitIds()).containsExactly(1L);
    }
}
