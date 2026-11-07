// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest
{
    private static final Account ALICE = new Account("alice", List.of(new Account.Identity("alice", "admin", List.of())), Set.of("analysts"), Set.of());

    private final List<String> withheld = new ArrayList<>();

    private static Inventory inventory()
    {
        return new Inventory("rs0", List.of("sales", "hr"), database -> "sales".equals(database) ? List.of("orders", "secret") : List.of("salaries"));
    }

    private Plan plan(MongoSnapshots policies, String... managed)
    {
        Set<String> names = Set.of(managed);
        return new Planner(policies.snapshot(), inventory(), database -> names.isEmpty() || names.contains(database), Instant.now(), withheld::add).plan(ALICE);
    }

    private static String[] on(String database, String collection)
    {
        return new String[] {"database", database, "collection", collection};
    }

    @Test
    void givesNothingWithoutAPolicy()
    {
        assertThat(plan(new MongoSnapshots()).empty()).isTrue();
    }

    @Test
    void grantsOnAWholeDatabaseWhereNothingBelowDecides()
    {
        Map<Target, ?> privileges = plan(new MongoSnapshots().allow(List.of("alice"), List.of("read", "insert"), on("sales", "*"))).privileges();
        assertThat(privileges).containsOnlyKeys(Target.database("sales"));
        assertThat(plan(new MongoSnapshots().allow(List.of("alice"), List.of("read", "insert"), on("sales", "*"))).privileges().get(Target.database("sales")))
                .containsExactlyInAnyOrder("find", "listIndexes", "collStats", "killCursors", "listCollections", "dbStats", "insert");
    }

    @Test
    void grantsByCollectionWhereAPolicyDecidesBelow()
    {
        Plan plan = plan(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "*")).deny(List.of("alice"), List.of("read"), on("sales", "secret")));
        assertThat(plan.privileges()).containsOnlyKeys(Target.collection("sales", "orders"));
        assertThat(plan.privileges().get(Target.collection("sales", "orders"))).containsExactlyInAnyOrder("find", "listIndexes", "collStats", "killCursors");
    }

    @Test
    void writingWholeDoesNotGiveListingOfNames()
    {
        Plan plan = plan(new MongoSnapshots().allow(List.of("alice"), List.of("update"), on("sales", "*")));
        assertThat(plan.privileges().get(Target.database("sales"))).containsExactly("update");
    }

    @Test
    void readsPoliciesForGroupsToo()
    {
        assertThat(plan(new MongoSnapshots().allow(List.of("@analysts"), List.of("read"), on("hr", "salaries"))).privileges()).containsOnlyKeys(Target.collection("hr", "salaries"));
    }

    @Test
    void grantsCreatingOnTheDatabase()
    {
        Plan plan = plan(new MongoSnapshots().allow(List.of("alice"), List.of("create"), "database", "sales"));
        assertThat(plan.privileges().get(Target.database("sales"))).containsExactly("createCollection");
    }

    @Test
    void dropsADatabaseOnlyWhereEveryCollectionMayBeDropped()
    {
        Plan both = plan(new MongoSnapshots().allow(List.of("alice"), List.of("drop_database"), "database", "sales").allow(List.of("alice"), List.of("drop"), on("sales", "*")));
        assertThat(both.privileges().get(Target.database("sales"))).containsExactlyInAnyOrder("dropCollection", "dropDatabase");
        assertThat(withheld).isEmpty();

        Plan some = plan(new MongoSnapshots().allow(List.of("alice"), List.of("drop_database"), "database", "sales").allow(List.of("alice"), List.of("drop"), on("sales", "orders")));
        assertThat(some.privileges()).containsOnlyKeys(Target.collection("sales", "orders"));
        assertThat(withheld).singleElement().asString().contains("dropping database sales for user alice");
    }

    @Test
    void grantsWhatDecidesTheDeployment()
    {
        Plan plan = plan(new MongoSnapshots().allow(List.of("alice"), List.of("list_databases"), "deployment", "rs*"));
        assertThat(plan.privileges().get(Target.DEPLOYMENT)).containsExactly("listDatabases");
        assertThat(plan(new MongoSnapshots().allow(List.of("alice"), List.of("monitor"), "deployment", "other")).empty()).isTrue();
    }

    @Test
    void aDatabaseNotLookedAfterByNameGetsOnlyWhatIsGrantedAsAWhole()
    {
        MongoSnapshots policies = new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "orders")).allow(List.of("alice"), List.of("read"), on("hr", "*"));
        Plan plan = plan(policies, "other");
        assertThat(plan.privileges()).containsOnlyKeys(Target.database("hr"));
        assertThat(plan(policies, "sales").privileges()).containsOnlyKeys(Target.database("hr"), Target.collection("sales", "orders"));
    }
}
