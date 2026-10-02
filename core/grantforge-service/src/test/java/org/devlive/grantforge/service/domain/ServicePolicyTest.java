// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServicePolicyTest
{
    @Test
    void holdsWhatThePolicySays()
    {
        ServicePolicy policy = ServicePolicy.create(42, PolicyType.DATA_MASK);
        assertThat(policy).extracting(ServicePolicy::getServiceId, ServicePolicy::getPolicyType, ServicePolicy::getPriority,
                ServicePolicy::isEnabled, ServicePolicy::getLabels, ServicePolicy::getBody)
                .containsExactly(42L, PolicyType.DATA_MASK, PolicyPriority.NORMAL, true, "[]", "{}");
        policy.describe("mask ssn", "hide numbers", PolicyPriority.OVERRIDE, false, "[\"pii\"]", "{\"allow\":[]}");
        assertThat(policy).extracting(ServicePolicy::getName, ServicePolicy::getDescription, ServicePolicy::getPriority,
                ServicePolicy::isEnabled, ServicePolicy::getLabels, ServicePolicy::getBody)
                .containsExactly("mask ssn", "hide numbers", PolicyPriority.OVERRIDE, false, "[\"pii\"]", "{\"allow\":[]}");
    }
}
