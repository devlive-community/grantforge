// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessReporterTest
{
    private static final Instant AT = Instant.parse("2026-11-07T08:00:00Z");

    private final List<AccessEvent> events = new ArrayList<>();
    private @Nullable Snapshot snapshot;
    private @Nullable Inventory inventory = new Inventory("rs0", List.of(), database -> List.of());

    private AccessReporter reporter()
    {
        return new AccessReporter("grantforge", () -> snapshot, user -> Set.of("analysts"), () -> inventory, events::add);
    }

    private static Accesses.Access read(String database, String collection)
    {
        return new Accesses.Access(Actions.READ, database, collection);
    }

    private static AccessReporter.Command command(String user, boolean allowed, Accesses.Access... accesses)
    {
        return new AccessReporter.Command(user, "find", "sales.orders", List.of(accesses), allowed, AT, "10.0.0.9");
    }

    private @Nullable Object field(int event, String name)
    {
        return EventFields.of(events.get(event)).get(name);
    }

    @Test
    void reportsWithoutPolicies()
    {
        reporter().report(command("alice", true, read("sales", "orders")));
        assertThat(field(0, "user")).isEqualTo("alice");
        assertThat(field(0, "resource")).isEqualTo("sales.orders");
        assertThat(field(0, "accessType")).isEqualTo("read");
        assertThat(field(0, "outcome")).isEqualTo("ALLOWED");
        assertThat(field(0, "enforcer")).isEqualTo("NATIVE");
        assertThat(field(0, "action")).isEqualTo("find");
        assertThat(field(0, "request")).isEqualTo("find sales.orders");
        assertThat(field(0, "resourceType")).isEqualTo("collection");
        assertThat(field(0, "clientIp")).isEqualTo("10.0.0.9");
        assertThat(field(0, "occurredAt")).isEqualTo("2026-11-07T08:00:00Z");
    }

    @Test
    void saysWhoDecided()
    {
        MongoSnapshots policies = new MongoSnapshots();
        long id = policies.nextId();
        snapshot = policies.allow(List.of("@analysts"), List.of("read"), "database", "sales", "collection", "orders").snapshot();
        reporter().report(command("alice", true, read("sales", "orders")));
        assertThat(field(0, "enforcer")).isEqualTo("GRANTFORGE");
        assertThat(field(0, "policyId")).isEqualTo(Long.toString(id));
        reporter().report(command("alice", true, read("hr", "salaries")));
        assertThat(field(1, "enforcer")).as("a role outside GrantForge allowed what no policy does").isEqualTo("NATIVE");
    }

    @Test
    void leavesOutTheAgentMongoDbsOwnUsersAndWhatNoPolicyDecides()
    {
        reporter().report(command("grantforge", true, read("sales", "orders")));
        reporter().report(command("__system", true, read("sales", "orders")));
        reporter().report(command("", true, read("sales", "orders")));
        reporter().report(command("alice", true));
        assertThat(events).isEmpty();
    }

    @Test
    void namesDatabasesAndTheDeployment()
    {
        reporter().report(new AccessReporter.Command("alice", "create", "sales.x", List.of(new Accesses.Access(Actions.CREATE, "sales", null)), true, AT, null));
        assertThat(field(0, "resource")).isEqualTo("sales");
        assertThat(field(0, "resourceType")).isEqualTo("database");
        reporter().report(new AccessReporter.Command("alice", "serverStatus", "admin", List.of(new Accesses.Access(Actions.MONITOR, null, null)), true, AT, null));
        assertThat(field(1, "resource")).isEqualTo("rs0");
        assertThat(field(1, "resourceType")).isEqualTo("deployment");
        inventory = null;
        snapshot = new MongoSnapshots().allow(List.of("alice"), List.of("monitor"), "deployment", "*").snapshot();
        reporter().report(new AccessReporter.Command("alice", "serverStatus", "admin", List.of(new Accesses.Access(Actions.MONITOR, null, null)), true, AT, null));
        assertThat(field(2, "resource")).as("before the first sync").isEqualTo(Inventory.STANDALONE);
    }

    @Test
    void aRefusalIsReportedForWhatThePoliciesDeny()
    {
        MongoSnapshots policies = new MongoSnapshots().allow(List.of("alice"), List.of("read"), "database", "sales", "collection", "orders");
        long id = policies.nextId();
        snapshot = policies.deny(List.of("alice"), List.of("read"), "database", "sales", "collection", "secret").snapshot();
        reporter().report(command("alice", false, read("sales", "orders"), read("sales", "secret")));
        assertThat(events).hasSize(1);
        assertThat(field(0, "resource")).isEqualTo("sales.secret");
        assertThat(field(0, "outcome")).isEqualTo("DENIED");
        assertThat(field(0, "policyId")).isEqualTo(Long.toString(id));
    }

    @Test
    void aRefusalThePoliciesDoNotExplainIsReportedForTheFirst()
    {
        snapshot = new MongoSnapshots().allow(List.of("alice"), List.of("read"), "database", "sales", "collection", "*").snapshot();
        reporter().report(command("alice", false, read("sales", "orders"), read("sales", "items")));
        assertThat(events).hasSize(1);
        assertThat(field(0, "resource")).isEqualTo("sales.orders");
        assertThat(field(0, "enforcer")).isEqualTo("NATIVE");
        snapshot = null;
        events.clear();
        reporter().report(command("alice", false, read("sales", "orders"), read("sales", "items")));
        assertThat(events).hasSize(1);
    }
}
