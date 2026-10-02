// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyItemSpecTest
{
    @SuppressWarnings("NullAway")
    @Test
    void tidiesNamesAndLeavesBlankExpressionsOut()
    {
        PolicyItemSpec item = new PolicyItemSpec(List.of(" alice ", "alice"), null, List.of("analyst"), List.of("select"), null, " ",
                "  ", " region = 'eu' ");
        assertThat(item.users()).containsExactly("alice");
        assertThat(item.groups()).isEmpty();
        assertThat(item.conditions()).isEmpty();
        assertThat(item.maskType()).isNull();
        assertThat(item.maskValue()).isNull();
        assertThat(item.rowFilter()).isEqualTo("region = 'eu'");
        assertThat(PolicyItemSpec.access(List.of("bob"), List.of("ops"), List.of("read")).roles()).isEmpty();
    }
}
