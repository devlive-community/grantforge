// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest
{
    private static SortedSet<String> actions(String... names)
    {
        return new TreeSet<>(List.of(names));
    }

    @Test
    void anEmptyPlanGivesNothing()
    {
        assertThat(Plan.NOTHING.empty()).isTrue();
        assertThat(Plan.NOTHING.documents()).isEmpty();
        Map<Target, SortedSet<String>> blank = new HashMap<>();
        blank.put(Target.database("sales"), actions());
        assertThat(Plan.of(blank).empty()).as("a target without actions is left out").isTrue();
    }

    @Test
    void writesThePrivilegesSorted()
    {
        Map<Target, SortedSet<String>> privileges = new HashMap<>();
        privileges.put(Target.collection("sales", "orders"), actions("update", "find"));
        privileges.put(Target.DEPLOYMENT, actions("top"));
        Plan plan = Plan.of(privileges);
        assertThat(plan.empty()).isFalse();
        assertThat(plan.documents()).containsExactly(new Document("resource", new Document("cluster", true)).append("actions", List.of("top")),
                new Document("resource", new Document("db", "sales").append("collection", "orders")).append("actions", List.of("find", "update")));
    }

    @Test
    void comparesByWhatItGives()
    {
        Plan one = Plan.of(Map.of(Target.database("sales"), actions("find", "insert")));
        Plan same = Plan.of(Map.of(Target.database("sales"), actions("insert", "find")));
        assertThat(one).isEqualTo(same);
        assertThat(one).isNotEqualTo(Plan.of(Map.of(Target.database("sales"), actions("find"))));
    }
}
