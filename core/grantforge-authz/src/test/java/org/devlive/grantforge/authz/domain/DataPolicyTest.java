// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataPolicyTest
{
    @Test
    void holdsWhatThePolicySaysAndCopiesToOtherRoles()
    {
        DataPolicy policy = DataPolicy.create(3, "user");
        assertThat(policy).extracting(DataPolicy::getRoleId, DataPolicy::getEntityCode, DataPolicy::getAction, DataPolicy::getScope,
                DataPolicy::getEffect).containsExactly(3L, "user", DataAction.READ, DataScope.SELF, GrantEffect.ALLOW);
        policy.describe(DataAction.EXPORT, DataScope.CUSTOM_ORGS, GrantEffect.DENY, null, "[1,2]");
        DataPolicy copy = policy.copyTo(4);
        assertThat(copy).extracting(DataPolicy::getRoleId, DataPolicy::getEntityCode, DataPolicy::getAction, DataPolicy::getScope,
                DataPolicy::getEffect, DataPolicy::getCondition, DataPolicy::getOrgUnitIds)
                .containsExactly(4L, "user", DataAction.EXPORT, DataScope.CUSTOM_ORGS, GrantEffect.DENY, null, "[1,2]");
    }
}
