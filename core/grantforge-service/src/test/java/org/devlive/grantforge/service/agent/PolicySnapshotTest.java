// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicySnapshotTest
{
    @Test
    void keepsItsOwnCopies()
    {
        List<String> implied = new ArrayList<>(List.of("select"));
        PolicySnapshot.Access access = new PolicySnapshot.Access("all", implied);
        implied.add("update");
        assertThat(access.impliedGrants()).containsExactly("select");
        Map<String, String> options = new HashMap<>(Map.of("a", "b"));
        PolicySnapshot.Condition condition = new PolicySnapshot.Condition("ip", "ip", options);
        options.clear();
        assertThat(condition.options()).containsEntry("a", "b");
        List<PolicySnapshot.Level> levels = new ArrayList<>();
        PolicySnapshot.Definition definition = new PolicySnapshot.Definition(levels, List.of(access), List.of(condition), List.of());
        Map<String, List<String>> roles = new HashMap<>();
        PolicySnapshot snapshot = new PolicySnapshot(PolicySnapshot.FORMAT, "dw", "hive", 1, true, 0, definition, List.of(), roles, Map.of());
        roles.put("x", List.of());
        assertThat(snapshot.roles()).isEmpty();
        assertThat(snapshot.format()).isEqualTo(1);
    }
}
