// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.GrantChange;
import org.devlive.grantforge.authz.application.Simulation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without the lists gives null
    void becomesASimulationAndTakesNoneForMissingLists()
    {
        SimulationRequest request = new SimulationRequest("7", List.of("1"), List.of("2"),
                List.of(new SimulationRequest.Grant("3", "4", GrantEffect.DENY), new SimulationRequest.Grant("3", "5", null)));
        assertThat(request.toSimulation()).isEqualTo(new Simulation(List.of(1L), List.of(2L), List.of(
                new Simulation.RoleGrantChange(3, new GrantChange(4, GrantEffect.DENY, null)),
                new Simulation.RoleGrantChange(3, new GrantChange(5, null, null)))));
        assertThat(new SimulationRequest(null, null, null, null).toSimulation()).isEqualTo(new Simulation(List.of(), List.of(), List.of()));
    }
}
