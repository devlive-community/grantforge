// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcilerTest
{
    private static final Inventory INVENTORY = new Inventory("rs0", List.of(), database -> List.of());
    private static final Plan READS = Plan.of(Map.of(Target.collection("sales", "orders"), new TreeSet<>(Set.of("find"))));

    private final FakeMongo mongo = new FakeMongo();
    private final Reconciler reconciler = new Reconciler(mongo, "gf_");

    private static Account.RoleRef agent(String role)
    {
        return new Account.RoleRef(role, "admin");
    }

    private static Account account(String name, Account.RoleRef... held)
    {
        return new Account(name, List.of(new Account.Identity(name, "admin", List.of(held)), new Account.Identity(name, "sales", List.of())), Set.of(), Set.of());
    }

    private static ServerState state(Map<String, ServerState.ManagedRole> managed, Account... accounts)
    {
        return new ServerState(List.of(accounts), Map.of(), managed, Map.of(), INVENTORY, true);
    }

    private static ServerState.ManagedRole held(Plan plan)
    {
        return new ServerState.ManagedRole(plan, false, true);
    }

    @Test
    void namesTheRoleOfAUser()
    {
        assertThat(reconciler.role("alice")).isEqualTo("gf_alice");
        assertThat(reconciler.role("CN=client,O=GrantForge")).startsWith("gf_CN_client_O_GrantForge_").matches("[A-Za-z0-9_-]+");
        assertThat(reconciler.role("a b")).isNotEqualTo(reconciler.role("a_b"));
        assertThat(reconciler.role("x".repeat(100))).hasSize(64);
    }

    @Test
    void createsTheRoleAndGrantsItToEveryIdentity()
    {
        Reconciler.Outcome outcome = reconciler.reconcile(state(Map.of(), account("alice")), account -> READS);
        assertThat(mongo.changes).containsExactly("admin createRole gf_alice", "admin grantRolesToUser alice", "sales grantRolesToUser alice");
        assertThat(mongo.changed.get(0)).isEqualTo(new Document("createRole", "gf_alice").append("privileges", READS.documents()).append("roles", List.of()));
        assertThat(mongo.changed.get(1).getList("roles", Document.class)).containsExactly(new Document("role", "gf_alice").append("db", "admin"));
        assertThat(outcome).isEqualTo(new Reconciler.Outcome(1, 1, 0, 0, 2, 0));
        assertThat(outcome.changed()).isTrue();
    }

    @Test
    void leavesWhatIsInStepAlone()
    {
        Account alice = new Account("alice", List.of(new Account.Identity("alice", "admin", List.of(agent("gf_alice"), agent("analysts")))), Set.of(), Set.of());
        Reconciler.Outcome outcome = reconciler.reconcile(state(Map.of("gf_alice", held(READS)), alice), account -> READS);
        assertThat(mongo.changes).isEmpty();
        assertThat(outcome.changed()).isFalse();
    }

    @Test
    void replacesPrivilegesInOneStep()
    {
        SortedSet<String> others = new TreeSet<>(Set.of("find", "insert"));
        Plan before = Plan.of(Map.of(Target.collection("sales", "orders"), others));
        Reconciler.Outcome outcome = reconciler.reconcile(state(Map.of("gf_alice", held(before)), account("alice", agent("gf_alice"))), account -> READS);
        assertThat(mongo.changes.get(0)).isEqualTo("admin updateRole gf_alice");
        assertThat(mongo.changed.get(0)).isEqualTo(new Document("updateRole", "gf_alice").append("privileges", READS.documents()).append("roles", List.of()));
        assertThat(outcome.updated()).isEqualTo(1);
    }

    @Test
    void setsRightARoleMadeToInheritOrHoldWhatItWouldNotWrite()
    {
        reconciler.reconcile(state(Map.of("gf_alice", new ServerState.ManagedRole(READS, true, true)), account("alice", agent("gf_alice"))), account -> READS);
        assertThat(mongo.changes).contains("admin updateRole gf_alice");
        mongo.changes.clear();
        reconciler.reconcile(state(Map.of("gf_alice", new ServerState.ManagedRole(READS, false, false)), account("alice", agent("gf_alice"))), account -> READS);
        assertThat(mongo.changes).contains("admin updateRole gf_alice");
    }

    @Test
    void dropsARoleNobodyNeeds()
    {
        Reconciler.Outcome outcome = reconciler.reconcile(state(Map.of("gf_alice", held(READS), "gf_gone", held(READS)), account("alice", agent("gf_alice"))), account -> Plan.NOTHING);
        assertThat(mongo.changes).contains("admin revokeRolesFromUser alice").contains("admin dropRole gf_alice", "admin dropRole gf_gone");
        assertThat(outcome.dropped()).isEqualTo(2);
    }

    @Test
    void withoutPlansGrantsNothing()
    {
        reconciler.reconcile(state(Map.of("gf_alice", held(READS))), null);
        assertThat(mongo.changes).containsExactly("admin dropRole gf_alice");
    }

    @Test
    void takesBackAnotherUsersRole()
    {
        Account bob = new Account("bob", List.of(new Account.Identity("bob", "admin", List.of(agent("gf_alice"), new Account.RoleRef("gf_other", "sales")))), Set.of(), Set.of());
        Reconciler.Outcome outcome = reconciler.reconcile(state(Map.of("gf_alice", held(READS)), account("alice", agent("gf_alice")), bob),
                account -> "alice".equals(account.name()) ? READS : Plan.NOTHING);
        assertThat(mongo.changes).contains("admin revokeRolesFromUser bob");
        Document revoke = mongo.changed.stream().filter(command -> "bob".equals(command.get("revokeRolesFromUser"))).findFirst().orElseThrow();
        assertThat(revoke.getList("roles", Document.class)).as("a role of that name in another database is not the agent's").containsExactly(new Document("role", "gf_alice").append("db", "admin"));
        assertThat(outcome.grants()).isEqualTo(2);
    }

    @Test
    void aUserWhoseChangeFailsDoesNotHoldBackTheOthers()
    {
        mongo.fail("createRole gf_alice", false);
        Reconciler.Outcome outcome = reconciler.reconcile(state(Map.of(), account("alice"), account("bob")), account -> READS);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(mongo.changes).contains("admin createRole gf_bob");
        assertThat(mongo.changes).doesNotContain("admin grantRolesToUser alice");
    }

    @Test
    void aRoleOfAUserThatFailedIsKept()
    {
        mongo.fail("updateRole gf_alice", false);
        reconciler.reconcile(state(Map.of("gf_alice", new ServerState.ManagedRole(READS, true, true)), account("alice", agent("gf_alice"))), account -> READS);
        assertThat(mongo.changes).doesNotContain("admin dropRole gf_alice");
    }

    @Test
    void aRoleThatCannotBeDroppedCountsAsFailed()
    {
        mongo.fail("dropRole", false);
        assertThat(reconciler.reconcile(state(Map.of("gf_gone", held(READS))), null).failed()).isEqualTo(1);
    }

    @Test
    void aDeploymentThatCannotBeReachedFailsTheSync()
    {
        mongo.fail("createRole", true);
        assertThatThrownBy(() -> reconciler.reconcile(state(Map.of(), account("alice")), account -> READS)).isInstanceOf(MongoFailure.class);
        FakeMongo down = new FakeMongo().fail("dropRole", true);
        assertThatThrownBy(() -> new Reconciler(down, "gf_").reconcile(state(Map.of("gf_gone", held(READS))), null)).isInstanceOf(MongoFailure.class);
    }
}
