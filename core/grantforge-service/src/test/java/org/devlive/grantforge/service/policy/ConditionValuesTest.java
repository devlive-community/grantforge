// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionValuesTest
{
    @SuppressWarnings("NullAway")
    @Test
    void tidiesTheTypeAndValues()
    {
        assertThat(new ConditionValues(" ip-range ", List.of(" 10.0.0.0/8 ", ""))).isEqualTo(new ConditionValues("ip-range",
                List.of("10.0.0.0/8")));
        assertThat(new ConditionValues(null, null).type()).isEmpty();
    }
}
