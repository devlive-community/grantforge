// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.authz.data.DataPolicyView;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataPolicyResponseTest
{
    @Test
    void sendsIdsAsTextAndTheConditionAsJson()
    {
        DataPolicyResponse response = DataPolicyResponse.from(new DataPolicyView(9_007_199_254_740_993L, 2, "user", DataAction.READ,
                DataScope.CONDITION, GrantEffect.ALLOW, "{\"field\":\"age\",\"op\":\"eq\",\"value\":1}", List.of(5L), Instant.EPOCH));
        assertThat(response.id()).isEqualTo("9007199254740993");
        assertThat(response.orgUnitIds()).containsExactly("5");
        assertThat(response.condition()).isNotNull();
        assertThat(DataPolicyResponse.from(new DataPolicyView(1, 2, "user", DataAction.READ, DataScope.SELF, GrantEffect.ALLOW, null,
                List.of(), Instant.EPOCH)).condition()).isNull();
    }
}
