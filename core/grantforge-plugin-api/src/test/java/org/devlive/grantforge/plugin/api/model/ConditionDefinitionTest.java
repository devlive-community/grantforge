// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionDefinitionTest
{
    @Test
    void conditionsNameTheirEvaluator()
    {
        ConditionDefinition window = new ConditionDefinition("hours", "Hours", "time-window", Map.of("zone", "UTC"));

        assertThat(window.options()).containsEntry("zone", "UTC");
        assertThat(ConditionDefinition.of("ip", "IP", "ip-range").options()).isEmpty();
        assertThatThrownBy(() -> ConditionDefinition.of("ip", "IP", "IP Range")).hasMessageContaining("evaluator");
    }
}
