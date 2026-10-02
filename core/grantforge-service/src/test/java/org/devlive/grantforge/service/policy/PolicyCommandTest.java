// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.service.domain.PolicyPriority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyCommandTest
{
    @Test
    void tidiesNameDescriptionAndLabels()
    {
        PolicyCommand command = new PolicyCommand(" sales ", " ", PolicyPriority.NORMAL, true, List.of("pii", " pii "),
                PolicyDocument.allowing(Map.of()));
        assertThat(command.name()).isEqualTo("sales");
        assertThat(command.description()).isNull();
        assertThat(command.labels()).containsExactly("pii");
    }
}
