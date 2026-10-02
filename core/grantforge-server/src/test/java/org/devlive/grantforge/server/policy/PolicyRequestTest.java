// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.policy;

import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.devlive.grantforge.service.policy.PolicyCommand;
import org.devlive.grantforge.service.policy.PolicyDocument;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NullAway")
class PolicyRequestTest
{
    @Test
    void fillsInWhatWasLeftOut()
    {
        PolicyRequest bare = new PolicyRequest(null, "sales", null, null, null, null, null, null);
        assertThat(bare.kind()).isEqualTo(PolicyType.ACCESS);
        PolicyCommand command = bare.command();
        assertThat(command.priority()).isEqualTo(PolicyPriority.NORMAL);
        assertThat(command.enabled()).isTrue();
        assertThat(command.labels()).isEmpty();
        assertThat(command.document().resources()).isEmpty();

        PolicyDocument document = PolicyDocument.allowing(Map.of());
        PolicyRequest full = new PolicyRequest(PolicyType.DATA_MASK, "mask", "d", PolicyPriority.OVERRIDE, false,
                Arrays.asList("pii", null), document, 3L);
        assertThat(full.kind()).isEqualTo(PolicyType.DATA_MASK);
        assertThat(full.labels()).containsExactly("pii");
        assertThat(full.command()).isEqualTo(new PolicyCommand("mask", "d", PolicyPriority.OVERRIDE, false, List.of("pii"), document));
    }
}
