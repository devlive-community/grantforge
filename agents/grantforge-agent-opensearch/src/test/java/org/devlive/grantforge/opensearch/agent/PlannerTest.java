// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Objects.requireNonNull;
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

    private Planner planner(OpenSearchSnapshots snapshots)
    {
        return new Planner(snapshots.snapshot(), inventory(), NOW, query -> {
            validated.add(query);
            return query.contains("bogus") ? "unknown query [bogus]" : null;
        });
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
        Plan plan = planner(new OpenSearchSnapshots().allow(List.of("alice"), List.of("read", "index"), "index", "logs-*", "field", "*")
                .allow(List.of("alice"), List.of("index"), "index", "logs-*")).plan(user("alice"));
        assertThat(plan.entries()).hasSize(1);
        Plan.Entry logs = plan.entries().get(0);
        assertThat(logs.indices()).containsExactly("logs-*");
        assertThat(logs.actions()).containsExactly("index", "read");
        assertThat(logs.fields()).isEmpty();
        assertThat(logs.documents()).isNull();
        assertThat(plan.cluster()).as("writing comes with bulk").containsExactly("cluster_composite_ops");
    }

    @Test
    void grantsByNameWhereAPolicyDecidesOtherwiseWithinAPattern()
    {
        Planner planner = planner(new OpenSearchSnapshots().allow(List.of("bob"), List.of("read"), "index", "logs-*", "field", "*")
                .deny(List.of("bob"), List.of("read"), "index", "logs-us", "field", "*"));
        Plan plan = planner.plan(user("bob"));
        assertThat(plan.entries()).extracting(entry -> Set.copyOf(entry.indices())).containsExactly(Set.of("logs-eu"));
        assertThat(plan.cluster()).containsExactly("cluster_composite_ops_ro");
        assertThat(planner.units()).extracting(Planner.Unit::representative).contains("logs-us", "logs-eu", "hr");
    }

    @Test
    void readsTheFieldsAPolicyAllowsWhenSomethingBelowTheIndexIsDecidedOtherwise()
    {
        Plan plan = planner(new OpenSearchSnapshots().allow(List.of("bob"), List.of("read"), "index", "hr", "field", "*")
                .deny(List.of("bob"), List.of("read"), "index", "hr", "field", "salary")
                .allow(List.of("carol"), List.of("read"), "index", "logs-eu", "field", "region")
                .allow(List.of("carol"), List.of("read"), "index", "logs-eu", "field", "later")).plan(user("bob"));
        assertThat(entry(plan, "hr").fields()).containsExactly("employee");
        Plan carol = planner(new OpenSearchSnapshots().allow(List.of("carol"), List.of("read"), "index", "logs-eu", "field", "region")
                .allow(List.of("carol"), List.of("read"), "index", "logs-eu", "field", "later")).plan(user("carol"));
        assertThat(entry(carol, "logs-eu").fields()).as("fields the policies name, though no document has them yet").containsExactly("later", "region");
        Plan none = planner(new OpenSearchSnapshots().allow(List.of("dave"), List.of("read"), "index", "hr", "field", "*")
                .deny(List.of("dave"), List.of("read"), "index", "hr", "field", "*")).plan(user("dave"));
        assertThat(none.empty()).isTrue();
    }

    @Test
    void groupsOfTheUserCountAndTheClusterIsGrantedByItsName()
    {
        Plan plan = planner(new OpenSearchSnapshots().allow(List.of("@ops"), List.of("cluster_monitor", "manage_snapshots"), "cluster", "search")
                .allow(List.of("@ops"), List.of("monitor", "manage"), "index", "hr")).plan(user("erin", "ops"));
        assertThat(plan.cluster()).containsExactly("cluster_monitor", "manage_snapshots");
        assertThat(entry(plan, "hr").actions()).as("manage gives what it implies too")
                .containsExactly("create_index", "indices_monitor", "manage", "manage_aliases");
        assertThat(planner(new OpenSearchSnapshots().allow(List.of("@ops"), List.of("cluster_monitor"), "cluster", "other")).plan(user("erin", "ops"))
                .empty()).isTrue();
        Planner nameless = new Planner(new OpenSearchSnapshots().allow(List.of("erin"), List.of("cluster_monitor"), "cluster", "*").snapshot(),
                new Inventory("", new TreeSet<>(), Map.of(), target -> new TreeSet<>()), NOW, query -> null);
        assertThat(nameless.plan(user("erin")).empty()).isTrue();
    }

    @Test
    void masksFieldsAsTheSecurityPluginWritesThem()
    {
        Plan plan = planner(new OpenSearchSnapshots().allow(List.of("erin"), List.of("read"), "index", "logs-eu", "field", "*")
                .mask(List.of("erin"), "mask_show_last_4", null, "logs-eu", "card").mask(List.of("erin"), "custom", "SHA-512", "logs-eu", "name")
                .mask(List.of("erin"), "mask_none", null, "logs-eu", "region")).plan(user("erin"));
        Plan.Entry logs = entry(plan, "logs-eu");
        assertThat(logs.fields()).isEmpty();
        assertThat(logs.masked()).containsExactly("card::/.(?=.{4})/::x", "name::SHA-512");
        assertThat(plan.notes()).isEmpty();
    }

    @Test
    void aMaskOpenSearchDoesNotKnowHidesTheField()
    {
        Plan whole = planner(new OpenSearchSnapshots().allow(List.of("erin"), List.of("read"), "index", "logs-eu", "field", "*")
                .mask(List.of("erin"), "custom", "NO-SUCH-HASH", "logs-eu", "card")).plan(user("erin"));
        assertThat(entry(whole, "logs-eu").fields()).containsExactly("~card");
        assertThat(whole.notes()).singleElement().asString().contains("masks card of logs-eu", "may not read the field");
        Plan some = planner(new OpenSearchSnapshots().allow(List.of("erin"), List.of("read"), "index", "logs-eu", "field", "card")
                .allow(List.of("erin"), List.of("read"), "index", "logs-eu", "field", "name").mask(List.of("erin"), "custom", null, "logs-eu", "card"))
                .plan(user("erin"));
        assertThat(entry(some, "logs-eu").fields()).containsExactly("name");
        Plan nothing = planner(new OpenSearchSnapshots().allow(List.of("erin"), List.of("read"), "index", "logs-eu", "field", "card")
                .mask(List.of("erin"), "custom", "/[/::x", "logs-eu", "card")).plan(user("erin"));
        assertThat(nothing.empty()).as("no field is left to read").isTrue();
    }

    @Test
    void filtersDocumentsWithQueriesOpenSearchRuns()
    {
        Plan plan = planner(new OpenSearchSnapshots().allow(List.of("erin"), List.of("read"), "index", "logs-*", "field", "*")
                .filter(List.of("erin"), "{\"term\": {\"region\": \"EU\"}}", "logs-eu").filter(List.of("erin"), "{\"bogus\": {}}", "logs-us"))
                .plan(user("erin"));
        assertThat(entry(plan, "logs-eu").documents()).isEqualTo("{\"term\": {\"region\": \"EU\"}}");
        assertThat(entry(plan, "logs-us").documents()).isEqualTo(Planner.NOTHING);
        assertThat(plan.notes()).singleElement().asString().contains("unknown query [bogus]", "sees none of them");
        assertThat(validated).hasSize(2);
    }

    @Test
    void entriesWithTheSamePermissionsAreOne()
    {
        Plan plan = planner(new OpenSearchSnapshots().allow(List.of("alice"), List.of("index"), "index", "hr")
                .allow(List.of("alice"), List.of("index"), "index", "logs-eu").deny(List.of("bob"), List.of("index"), "index", "nothing"))
                .plan(user("alice"));
        assertThat(plan.entries()).singleElement().extracting(Plan.Entry::indices).isEqualTo(Set.of("hr", "logs-eu"));
    }

    @Test
    void overlappingUniformPatternsShareAnEntry()
    {
        Planner planner = planner(new OpenSearchSnapshots().allow(List.of("alice"), List.of("index"), "index", "logs-*|log?-*"));
        assertThat(planner.units()).extracting(unit -> Set.copyOf(unit.indices())).contains(Set.of("log?-*", "logs-*"));
        assertThat(planner.plan(user("alice")).entries()).singleElement().extracting(Plan.Entry::indices).isEqualTo(Set.of("log?-*", "logs-*"));
    }

    @Test
    void writesMasksTheSecurityPluginKnows()
    {
        assertThat(Planner.masked(mask("{col}::SHA-256", null), "card")).isEqualTo("card::SHA-256");
        assertThat(Planner.masked(mask("{col}", null), "card")).isEqualTo("card");
        assertThat(Planner.masked(mask("{col}::{expr}", "/a/::b::/c/::d"), "card")).isEqualTo("card::/a/::b::/c/::d");
        assertThat(Planner.masked(mask("{col}::{expr}", "/a/::b::/c/"), "card")).as("a regular expression without a replacement").isNull();
        assertThat(Planner.masked(mask("{col}::{expr}", "a/::b"), "card")).isNull();
        assertThat(Planner.masked(mask("{col}::{expr}", "/(/::b"), "card")).isNull();
        assertThat(Planner.masked(mask("{col}::{expr}", "//::b"), "card")).isNull();
        assertThat(Planner.masked(mask("{col}::{expr}", "SHA 256"), "card")).isNull();
        assertThat(Planner.masked(mask("{col}:{expr}", "x"), "card")).isNull();
        assertThat(Planner.masked(mask("hash({col})", null), "card")).isNull();
        assertThat(Planner.masked(mask(null, null), "card")).isNull();
        assertThat(Planner.masked(mask("{col}::{expr}", null), "card")).isNull();
    }

    private static DataMask mask(@Nullable String transformer, @Nullable String value)
    {
        return requireNonNull(new OpenSearchSnapshots().maskType("probe", transformer).mask(List.of("erin"), "probe", value, "i", "f").snapshot()
                .mask(AccessRequest.builder("erin", "read").resource("index", "i").resource("field", "f").build()));
    }
}
