// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationResultTest
{
    @Test
    void copiesItsLists()
    {
        List<EffectiveAccess.Item> gained = new ArrayList<>(List.of(new EffectiveAccess.Item("system", "System", null, ResourceType.MODULE, null)));
        SimulationResult result = new SimulationResult(List.of("a"), List.of(), gained, List.of(), List.of(), List.of());
        gained.clear();
        assertThat(result.gainedResources()).hasSize(1);
        assertThat(result.rolesBefore()).containsExactly("a");
    }
}
