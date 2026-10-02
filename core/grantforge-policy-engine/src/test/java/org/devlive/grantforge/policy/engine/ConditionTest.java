// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTest
{
    @Test
    void namesItsTypeAndValues()
    {
        Condition condition = Condition.of("ip-range", "10.0.0.0/8");
        assertThat(condition.type()).isEqualTo("ip-range");
        assertThat(condition.values()).containsExactly("10.0.0.0/8");
        assertThatThrownBy(() -> Condition.of("")).isInstanceOf(IllegalArgumentException.class);
    }
}
