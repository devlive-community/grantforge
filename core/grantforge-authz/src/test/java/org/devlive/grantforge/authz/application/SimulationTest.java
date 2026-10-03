// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SimulationTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void copiesItsChanges()
    {
        List<Long> added = new ArrayList<>(List.of(1L));
        Simulation simulation = new Simulation(added, List.of(), List.of(new Simulation.RoleGrantChange(1, new GrantChange(2, null, null))));
        added.clear();
        assertThat(simulation.addRoles()).containsExactly(1L);
        assertThat(simulation.grants()).hasSize(1);
        assertThatNullPointerException().isThrownBy(() -> new Simulation.RoleGrantChange(1, null));
    }
}
