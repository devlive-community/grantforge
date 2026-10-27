// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcilerTest
{
    private static final TableRef ORDERS = new TableRef("internal", "sales", "orders");
    private static final TableRef ITEMS = new TableRef("internal", "sales", "items");
    private static final String ALICE = "'alice'@'%'";

    private final FakeDoris doris = new FakeDoris();
    private final Reconciler reconciler = new Reconciler(doris, "grantforge_");

    private static Account alice(Set<String> roles, Map<TableRef, Set<String>> columns)
    {
        return new Account("alice", List.of(ALICE), Set.of(), Map.of(ALICE, roles), Map.of(ALICE, columns));
    }

    private static ServerState state(List<Account> accounts, Map<String, Set<Privilege>> roles, Map<ServerState.RowPolicyKey, ServerState.RowPolicy> policies)
    {
        return new ServerState("grantforge", accounts, List.of(), roles, Map.of(), Map.of(), policies, PlannerTest.inventory());
    }

    @Test
    void namesRolesAfterTheirUsers()
    {
        assertThat(reconciler.role("alice")).isEqualTo("grantforge_alice");
        assertThat(reconciler.role("bob-1")).isEqualTo("grantforge_bob-1");
        assertThat(reconciler.role("o.brien")).as("Doris takes no dot in a role name").startsWith("grantforge_o_brien_").hasSize(27);
        assertThat(reconciler.role("o_brien")).isNotEqualTo(reconciler.role("o.brien"));
        assertThat(reconciler.role("x".repeat(80))).hasSize(64);
    }

    @Test
    void createsTheRoleGrantsItsPrivilegesAndGivesItToEveryIdentity()
    {
        Account alice = new Account("alice", List.of(ALICE, "'alice'@'10.%'"), Set.of(), Map.of(ALICE, Set.of()), Map.of());
        Plan plan = new Plan(List.of(Privilege.database("SELECT_PRIV", "internal", "sales"), Privilege.database("LOAD_PRIV", "internal", "sales"),
                Privilege.workloadGroup("USAGE_PRIV", "etl")), Map.of(), Map.of());
        Reconciler.Outcome outcome = reconciler.reconcile(state(List.of(alice), Map.of(), Map.of()), account -> plan);
        assertThat(doris.executed).containsExactly("CREATE ROLE `grantforge_alice` COMMENT 'GrantForge agent role of user alice'",
                "GRANT LOAD_PRIV, SELECT_PRIV ON `internal`.`sales`.* TO ROLE 'grantforge_alice'",
                "GRANT USAGE_PRIV ON WORKLOAD GROUP 'etl' TO ROLE 'grantforge_alice'", "GRANT 'grantforge_alice' TO 'alice'@'%'",
                "GRANT 'grantforge_alice' TO 'alice'@'10.%'");
        assertThat(outcome).isEqualTo(new Reconciler.Outcome(1, 1, 0, 5, 0, 0));
        assertThat(outcome.changed()).isTrue();
        assertThat(reconciler.plans()).containsEntry("alice", plan);
    }

    @Test
    void revokesWhatTheRoleHoldsBeyondItsPlanAndSendsNothingWhenInStep()
    {
        Account alice = alice(Set.of("grantforge_alice"), Map.of());
        Plan plan = new Plan(List.of(Privilege.table("SELECT_PRIV", ORDERS)), Map.of(), Map.of());
        Set<Privilege> held = Set.of(Privilege.table("SELECT_PRIV", ORDERS), Privilege.database("SELECT_PRIV", "internal", "hr"),
                new Privilege("SELECT_PRIV", "odd.na.me.x", false));
        reconciler.reconcile(state(List.of(alice), Map.of("grantforge_alice", held), Map.of()), account -> plan);
        assertThat(doris.executed).containsExactly("REVOKE SELECT_PRIV ON `internal`.`hr`.* FROM ROLE 'grantforge_alice'");

        doris.executed.clear();
        Reconciler.Outcome outcome = reconciler.reconcile(state(List.of(alice), Map.of("grantforge_alice", plan.privilegeSet()), Map.of()),
                account -> plan);
        assertThat(doris.executed).isEmpty();
        assertThat(outcome.changed()).isFalse();
    }

    @Test
    void grantsColumnsTheUserCannotReadAndRevokesTheOthersOnce()
    {
        Plan plan = new Plan(List.of(), Map.of(ORDERS, new TreeSet<>(List.of("id", "region"))), Map.of());
        Account lacking = alice(Set.of("grantforge_alice"), Map.of(ORDERS, Set.of("id", "Card"), ITEMS, Set.of("sku")));
        reconciler.reconcile(state(List.of(lacking), Map.of("grantforge_alice", Set.of()), Map.of()), account -> plan);
        assertThat(doris.executed).containsExactly("GRANT SELECT_PRIV(`id`, `region`) ON `internal`.`sales`.`orders` TO ROLE 'grantforge_alice'",
                "REVOKE SELECT_PRIV(`Card`) ON `internal`.`sales`.`orders` FROM ROLE 'grantforge_alice'",
                "REVOKE SELECT_PRIV(`sku`) ON `internal`.`sales`.`items` FROM ROLE 'grantforge_alice'");

        doris.executed.clear();
        Account outside = alice(Set.of("grantforge_alice"), Map.of(ORDERS, Set.of("ID", "Region", "Card"), ITEMS, Set.of("sku")));
        reconciler.reconcile(state(List.of(outside), Map.of("grantforge_alice", Set.of()), Map.of()), account -> plan);
        assertThat(doris.executed).as("columns a revoke left readable come from elsewhere").isEmpty();

        Account cleaned = alice(Set.of("grantforge_alice"), Map.of(ORDERS, Set.of("id", "region")));
        reconciler.reconcile(state(List.of(cleaned), Map.of("grantforge_alice", Set.of()), Map.of()), account -> plan);
        reconciler.reconcile(state(List.of(outside), Map.of("grantforge_alice", Set.of()), Map.of()), account -> plan);
        assertThat(doris.executed).as("columns that come back are revoked again").hasSize(2);
    }

    @Test
    void putsARowFilterInPlaceBeforeThePrivilegesAndReplacesItBehindAHold()
    {
        Account alice = alice(Set.of(), Map.of());
        Plan eu = new Plan(List.of(Privilege.table("SELECT_PRIV", ORDERS)), Map.of(), Map.of(ORDERS, new Plan.Filter("region = 'EU'", 3)));
        reconciler.reconcile(state(List.of(alice), Map.of(), Map.of()), account -> eu);
        assertThat(doris.executed).containsSubsequence("CREATE ROW POLICY `grantforge_alice` ON `internal`.`sales`.`orders` AS PERMISSIVE TO ROLE"
                + " `grantforge_alice` USING (region = 'EU')", "GRANT SELECT_PRIV ON `internal`.`sales`.`orders` TO ROLE 'grantforge_alice'");

        ServerState.RowPolicyKey key = new ServerState.RowPolicyKey("grantforge_alice", ORDERS);
        Map<ServerState.RowPolicyKey, ServerState.RowPolicy> shown = Map.of(key, new ServerState.RowPolicy(false, "(region = 'EU')", null,
                "grantforge_alice"));
        Map<String, Set<Privilege>> held = Map.of("grantforge_alice", eu.privilegeSet());
        Account member = alice(Set.of("grantforge_alice"), Map.of());
        doris.executed.clear();
        assertThat(reconciler.reconcile(state(List.of(member), held, shown), account -> eu).changed()).isFalse();

        Plan us = new Plan(eu.privileges(), Map.of(), Map.of(ORDERS, new Plan.Filter("region = 'US'", 3)));
        reconciler.reconcile(state(List.of(member), held, shown), account -> us);
        assertThat(doris.executed).containsExactly(
                "CREATE ROW POLICY `grantforge_alice_hold` ON `internal`.`sales`.`orders` AS RESTRICTIVE TO ROLE `grantforge_alice` USING (1 = 0)",
                "DROP ROW POLICY IF EXISTS `grantforge_alice` ON `internal`.`sales`.`orders`",
                "CREATE ROW POLICY `grantforge_alice` ON `internal`.`sales`.`orders` AS PERMISSIVE TO ROLE `grantforge_alice` USING (region = 'US')",
                "DROP ROW POLICY IF EXISTS `grantforge_alice_hold` ON `internal`.`sales`.`orders`");
    }

    @Test
    void takesAFilterAsDorisShowsItAfterARestart()
    {
        Map<ServerState.RowPolicyKey, ServerState.RowPolicy> shown = Map.of(new ServerState.RowPolicyKey("grantforge_alice", ORDERS),
                new ServerState.RowPolicy(false, "(`region` = 'EU')", null, "grantforge_alice"));
        Plan eu = new Plan(List.of(Privilege.table("SELECT_PRIV", ORDERS)), Map.of(), Map.of(ORDERS, new Plan.Filter("region  =  'EU'", 3)));
        reconciler.reconcile(state(List.of(alice(Set.of("grantforge_alice"), Map.of())), Map.of("grantforge_alice", eu.privilegeSet()), shown),
                account -> eu);
        assertThat(doris.executed).isEmpty();
        assertThat(Reconciler.same("((a = 1) OR (b = 2))", "(a = 1) OR (b = 2)")).isTrue();
        assertThat(Reconciler.same("(a = 1) OR (b = 2)", "a = 1) OR (b = 2")).isFalse();
    }

    @Test
    void aFilterDorisRefusesShowsNoRows()
    {
        doris.fail("USING (no_such = 1)", false);
        Plan plan = new Plan(List.of(Privilege.table("SELECT_PRIV", ORDERS)), Map.of(), Map.of(ORDERS, new Plan.Filter("no_such = 1", 4)));
        reconciler.reconcile(state(List.of(alice(Set.of("grantforge_alice"), Map.of())), Map.of("grantforge_alice", Set.of()), Map.of()),
                account -> plan);
        assertThat(doris.executed).contains("CREATE ROW POLICY `grantforge_alice` ON `internal`.`sales`.`orders` AS PERMISSIVE TO ROLE"
                + " `grantforge_alice` USING (1 = 0)");
    }

    @Test
    void aFilterTheTableCannotRunShowsNoRows()
    {
        doris.fail("WHERE (missing = 1)", "Unknown column 'missing' in 'table list'");
        Plan plan = new Plan(List.of(Privilege.table("SELECT_PRIV", ORDERS)), Map.of(), Map.of(ORDERS, new Plan.Filter("missing = 1", 4)));
        reconciler.reconcile(state(List.of(alice(Set.of("grantforge_alice"), Map.of())), Map.of("grantforge_alice", Set.of()), Map.of()),
                account -> plan);
        assertThat(doris.executed).noneMatch(sql -> sql.contains("missing")).contains("CREATE ROW POLICY `grantforge_alice` ON"
                + " `internal`.`sales`.`orders` AS PERMISSIVE TO ROLE `grantforge_alice` USING (1 = 0)");
    }

    @Test
    void dropsRolesAndRowPoliciesNoPlanNeedsAndLeftoverHolds()
    {
        ServerState.RowPolicyKey bobs = new ServerState.RowPolicyKey("grantforge_bob", ORDERS);
        ServerState.RowPolicyKey hold = new ServerState.RowPolicyKey("grantforge_alice_hold", ORDERS);
        ServerState.RowPolicyKey alices = new ServerState.RowPolicyKey("grantforge_alice", ITEMS);
        ServerState.RowPolicyKey others = new ServerState.RowPolicyKey("theirs", ORDERS);
        ServerState.RowPolicy any = new ServerState.RowPolicy(false, "(1 = 1)", null, "x");
        Plan plan = new Plan(List.of(Privilege.table("SELECT_PRIV", ORDERS)), Map.of(), Map.of());
        Reconciler.Outcome outcome = reconciler.reconcile(state(List.of(alice(Set.of("grantforge_alice"), Map.of())), Map.of("grantforge_alice",
                plan.privilegeSet(), "grantforge_bob", Set.of()), Map.of(bobs, any, hold, any, alices, any, others, any)), account -> plan);
        assertThat(doris.executed).contains("DROP ROLE IF EXISTS `grantforge_bob`",
                "DROP ROW POLICY IF EXISTS `grantforge_bob` ON `internal`.`sales`.`orders`",
                "DROP ROW POLICY IF EXISTS `grantforge_alice_hold` ON `internal`.`sales`.`orders`",
                "DROP ROW POLICY IF EXISTS `grantforge_alice` ON `internal`.`sales`.`items`").noneMatch(sql -> sql.contains("theirs"));
        assertThat(outcome.dropped()).isEqualTo(1);
        assertThat(outcome.rowPolicies()).isEqualTo(3);
    }

    @Test
    void withoutPlansEveryRoleGoes()
    {
        reconciler.reconcile(state(List.of(alice(Set.of("grantforge_alice"), Map.of())), Map.of("grantforge_alice", Set.of()), Map.of()), null);
        assertThat(doris.executed).containsExactly("DROP ROLE IF EXISTS `grantforge_alice`");
    }

    @Test
    void aPlanThatFailsKeepsTheRoleAndARefusalFailsTheUserOnly()
    {
        Reconciler.Outcome broken = reconciler.reconcile(state(List.of(alice(Set.of("grantforge_alice"), Map.of())), Map.of("grantforge_alice",
                Set.of()), Map.of()), account -> {
                    throw new IllegalStateException("broken");
                });
        assertThat(doris.executed).isEmpty();
        assertThat(broken.failed()).isEqualTo(1);

        doris.fail("GRANT", false);
        Plan plan = new Plan(List.of(Privilege.table("SELECT_PRIV", ORDERS)), Map.of(), Map.of());
        assertThat(reconciler.reconcile(state(List.of(alice(Set.of(), Map.of())), Map.of("grantforge_alice", Set.of()), Map.of()), account -> plan)
                .failed()).isEqualTo(1);

        doris.fail("GRANT", true);
        assertThatThrownBy(() -> reconciler.reconcile(state(List.of(alice(Set.of(), Map.of())), Map.of("grantforge_alice", Set.of()), Map.of()),
                account -> plan)).isInstanceOf(DorisFailure.class);
        assertThatThrownBy(() -> reconciler.reconcile(state(List.of(alice(Set.of(), Map.of())), Map.of(), Map.of()), account -> {
            throw new DorisFailure("gone", true, null);
        })).isInstanceOf(DorisFailure.class);
    }
}
