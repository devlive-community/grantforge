// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest
{
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Map<String, SortedSet<String>> FIELDS = Map.of("logs-eu", new TreeSet<>(Set.of("card", "name", "region")),
            "logs-us", new TreeSet<>(Set.of("amount", "region")), "hr", new TreeSet<>(Set.of("employee", "salary")));

    private final List<String> validated = new ArrayList<>();

    private static Inventory inventory()
    {
        return new Inventory("search", new TreeSet<>(Set.of("hr", "logs-eu", "logs-us")), Map.of(), target -> {
            SortedSet<String> fields = new TreeSet<>();
            for (String each : target.split(",")) {
                FIELDS.forEach((index, those) -> {
                    if (Patterns.glob(each).run(index)) {
                        fields.addAll(those);
                    }
                });
            }
            return fields;
        });
    }

    private Planner planner(ElasticsearchSnapshots snapshots)
    {
        return planner(snapshots, true);
    }

    private Planner planner(ElasticsearchSnapshots snapshots, boolean restricting)
    {
        return new Planner(snapshots.snapshot(), inventory(), NOW, query -> {
            validated.add(query);
            return query.contains("bogus") ? "unknown query [bogus]" : null;
        }, restricting);
    }

    private static Account user(String name, String... groups)
    {
        return new Account(name, Set.of(groups), true);
    }

    private static Plan.Entry entry(Plan plan, String index)
    {
        return plan.entries().stream().filter(entry -> entry.indices().contains(index)).findFirst().orElseThrow();
    }

    @Test
    void grantsAUniformPatternAsAPatternWithoutFieldLevelSecurity()
    {
        Plan plan = planner(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read", "index"), "index", "logs-*", "field", "*")
                .allow(List.of("alice"), List.of("index"), "index", "logs-*")).plan(user("alice"));
        assertThat(plan.entries()).hasSize(1);
        Plan.Entry logs = plan.entries().get(0);
        assertThat(logs.indices()).containsExactly("logs-*");
        assertThat(logs.privileges()).containsExactly("index", "read");
        assertThat(logs.fields()).isEmpty();
        assertThat(logs.documents()).isNull();
        assertThat(plan.cluster()).as("Elasticsearch needs no cluster privilege for bulk or multi-search").isEmpty();
        assertThat(plan.notes()).isEmpty();
    }

    @Test
    void grantsByNameWhereAPolicyDecidesOtherwiseWithinAPattern()
    {
        Planner planner = planner(new ElasticsearchSnapshots().allow(List.of("bob"), List.of("read"), "index", "logs-*", "field", "*")
                .deny(List.of("bob"), List.of("read"), "index", "logs-us", "field", "*"));
        Plan plan = planner.plan(user("bob"));
        assertThat(plan.entries()).extracting(entry -> Set.copyOf(entry.indices())).containsExactly(Set.of("logs-eu"));
        assertThat(planner.units()).extracting(Planner.Unit::representative).contains("logs-us", "logs-eu", "hr");
    }

    @Test
    void readsTheFieldsAPolicyAllowsWhenSomethingBelowTheIndexIsDecidedOtherwise()
    {
        Plan plan = planner(new ElasticsearchSnapshots().allow(List.of("bob"), List.of("read"), "index", "hr", "field", "*")
                .deny(List.of("bob"), List.of("read"), "index", "hr", "field", "salary")).plan(user("bob"));
        assertThat(entry(plan, "hr").fields()).containsExactly("employee");
        Plan carol = planner(new ElasticsearchSnapshots().allow(List.of("carol"), List.of("read"), "index", "logs-eu", "field", "region")
                .allow(List.of("carol"), List.of("read"), "index", "logs-eu", "field", "later")).plan(user("carol"));
        assertThat(entry(carol, "logs-eu").fields()).as("fields the policies name, though no document has them yet").containsExactly("later", "region");
        Plan none = planner(new ElasticsearchSnapshots().allow(List.of("dave"), List.of("read"), "index", "hr", "field", "*")
                .deny(List.of("dave"), List.of("read"), "index", "hr", "field", "*")).plan(user("dave"));
        assertThat(none.empty()).isTrue();
    }

    @Test
    void groupsOfTheUserCountAndTheClusterIsGrantedByItsName()
    {
        Plan plan = planner(new ElasticsearchSnapshots().allow(List.of("@ops"), List.of("cluster_monitor", "manage_slm"), "cluster", "search")
                .allow(List.of("@ops"), List.of("manage"), "index", "hr")).plan(user("erin", "ops"));
        assertThat(plan.cluster()).as("manage_slm gives create_snapshot too").containsExactly("create_snapshot", "manage_slm", "monitor");
        assertThat(entry(plan, "hr").privileges()).as("manage gives what it implies too")
                .containsExactly("create_index", "delete_index", "manage", "monitor", "view_index_metadata");
        assertThat(planner(new ElasticsearchSnapshots().allow(List.of("@ops"), List.of("cluster_monitor"), "cluster", "other")).plan(user("erin", "ops"))
                .empty()).isTrue();
        Planner nameless = new Planner(new ElasticsearchSnapshots().allow(List.of("erin"), List.of("cluster_monitor"), "cluster", "*").snapshot(),
                new Inventory("", new TreeSet<>(), Map.of(), target -> new TreeSet<>()), NOW, query -> null, true);
        assertThat(nameless.plan(user("erin")).empty()).isTrue();
    }

    @Test
    void filtersDocumentsWithQueriesElasticsearchRuns()
    {
        Plan plan = planner(new ElasticsearchSnapshots().allow(List.of("erin"), List.of("read"), "index", "logs-*", "field", "*")
                .filter(List.of("erin"), "{\"term\": {\"region\": \"EU\"}}", "logs-eu").filter(List.of("erin"), "{\"bogus\": {}}", "logs-us"))
                .plan(user("erin"));
        assertThat(entry(plan, "logs-eu").documents()).isEqualTo("{\"term\": {\"region\": \"EU\"}}");
        assertThat(entry(plan, "logs-us").documents()).isEqualTo(Planner.NOTHING);
        assertThat(plan.notes()).singleElement().asString().contains("unknown query [bogus]", "sees none of them");
        assertThat(validated).hasSize(2);
    }

    @Test
    void belowPlatinumGrantsNoReadThatNeedsFieldOrDocumentLevelSecurity()
    {
        Plan plan = planner(new ElasticsearchSnapshots().allow(List.of("bob"), List.of("read", "index"), "index", "hr", "field", "*")
                .deny(List.of("bob"), List.of("read"), "index", "hr", "field", "salary")
                .allow(List.of("bob"), List.of("read"), "index", "logs-*", "field", "*")
                .filter(List.of("bob"), "{\"term\": {\"region\": \"EU\"}}", "logs-eu"), false).plan(user("bob"));
        assertThat(entry(plan, "hr").privileges()).as("writing stays, reading some fields does not").containsExactly("index");
        assertThat(entry(plan, "hr").fields()).isEmpty();
        assertThat(plan.entries()).noneMatch(entry -> entry.indices().contains("logs-eu")).noneMatch(entry -> entry.documents() != null);
        assertThat(entry(plan, "logs-us").privileges()).as("a whole index needs neither").containsExactly("read");
        assertThat(plan.notes()).hasSize(2).allMatch(note -> note.contains("below Platinum"));
        assertThat(plan.notes()).anyMatch(note -> note.contains("some fields of hr")).anyMatch(note -> note.contains("some documents of logs-eu"));
        assertThat(validated).as("no query is checked that will not be granted").isEmpty();
    }

    @Test
    void entriesWithTheSamePrivilegesAreOne()
    {
        Plan plan = planner(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("index"), "index", "hr")
                .allow(List.of("alice"), List.of("index"), "index", "logs-eu").deny(List.of("bob"), List.of("index"), "index", "nothing"))
                .plan(user("alice"));
        assertThat(plan.entries()).singleElement().extracting(Plan.Entry::indices).isEqualTo(Set.of("hr", "logs-eu"));
    }

    @Test
    void overlappingUniformPatternsShareAnEntry()
    {
        Planner planner = planner(new ElasticsearchSnapshots().allow(List.of("alice"), List.of("index"), "index", "logs-*|log?-*"));
        assertThat(planner.units()).extracting(unit -> Set.copyOf(unit.indices())).contains(Set.of("log?-*", "logs-*"));
        assertThat(planner.plan(user("alice")).entries()).singleElement().extracting(Plan.Entry::indices).isEqualTo(Set.of("log?-*", "logs-*"));
    }
}
