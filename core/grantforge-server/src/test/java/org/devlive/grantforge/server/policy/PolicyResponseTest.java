// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.policy;

import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.policy.PolicyDocument;
import org.devlive.grantforge.service.policy.PolicyView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyResponseTest
{
    @Test
    void sendsIdsAsText()
    {
        PolicyView view = new PolicyView(9_007_199_254_740_993L, 5, "sales", null, PolicyType.ACCESS, PolicyPriority.NORMAL, true,
                List.of("pii"), PolicyDocument.allowing(Map.of()), 2, Instant.EPOCH);
        PolicyResponse response = PolicyResponse.from(view);
        assertThat(response.id()).isEqualTo("9007199254740993");
        assertThat(response.serviceId()).isEqualTo("5");
        assertThat(response.labels()).containsExactly("pii");
        assertThat(response.version()).isEqualTo(2);
    }
}
