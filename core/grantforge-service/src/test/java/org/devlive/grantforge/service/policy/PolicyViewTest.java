// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyViewTest
{
    @Test
    void keepsItsOwnCopyOfTheLabels()
    {
        List<String> labels = new ArrayList<>(List.of("pii"));
        PolicyView view = new PolicyView(1, 2, "sales", null, PolicyType.ACCESS, PolicyPriority.NORMAL, true, labels,
                PolicyDocument.allowing(Map.of()), 0, Instant.EPOCH);
        labels.add("eu");
        assertThat(view.labels()).containsExactly("pii");
    }
}
