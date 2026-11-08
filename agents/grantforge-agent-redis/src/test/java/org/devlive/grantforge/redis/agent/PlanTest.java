// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest
{
    @Test
    void addsTheMonitoringSelectorOnTheNodesItNames()
    {
        Selector keys = Selector.of(List.of("+@read"), new TreeSet<>(List.of("%R~a")), new TreeSet<>());
        Plan plan = new Plan(List.of(keys), Set.of("p:6379"));
        assertThat(plan.on("r:6379")).containsExactly(keys);
        assertThat(plan.on("p:6379")).hasSize(2).extracting(Selector::rule).contains("(-@all +info +dbsize +latency|latest +latency|history +memory|stats)");
        assertThat(Plan.NOTHING.on("p:6379")).isEmpty();
    }
}
