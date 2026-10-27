// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class NativeConflictsTest
{
    private static final TableRef ORDERS = PlannerTest.ORDERS;
    private static final TableRef SALARIES = PlannerTest.SALARIES;
    private static final String DAVE = "'dave'@'%'";

    private static String[] column(String database, String table, String column)
    {
        return new String[] {"catalog", "internal", "database", database, "table", table, "column", column};
    }

    private static ServerState state(Account account, Map<String, Set<Privilege>> otherRoles, Set<Privilege> direct,
            Map<ServerState.RowPolicyKey, ServerState.RowPolicy> policies)
    {
        return new ServerState("grantforge", List.of(account), List.of("boss"), Map.of(), otherRoles, Map.of(DAVE, direct), policies,
                PlannerTest.inventory());
    }

    @Test
    void reportsGrantsOfOtherRolesAndOfTheUserThatPoliciesDenyOnce()
    {
        DorisSnapshots policies = new DorisSnapshots().deny(List.of("dave"), List.of("select"), column("hr", "salaries", "*"))
                .deny(List.of("dave"), List.of("usage"), "workload_group", "etl");
        Account dave = new Account("dave", List.of(DAVE), Set.of("analysts"), Map.of(DAVE, Set.of("analysts")), Map.of());
        Set<Privilege> direct = Set.of(Privilege.table("SELECT_PRIV", SALARIES), Privilege.workloadGroup("USAGE_PRIV", "etl"),
                Privilege.database("SELECT_PRIV", "internal", "mysql"));
        ServerState state = state(dave, Map.of("analysts", Set.of(Privilege.database("SELECT_PRIV", "internal", "hr"))), direct, Map.of());
        NativeConflicts conflicts = new NativeConflicts();
        List<NativeConflicts.Conflict> found = conflicts.check(state, policies.snapshot(), Map.of(), user -> "grantforge_" + user, Instant.now());
        assertThat(found).extracting(NativeConflicts.Conflict::message).containsExactlyInAnyOrder(
                "Doris user boss holds ADMIN_PRIV or NODE_PRIV, which no role binds; GrantForge policies do not apply to it",
                "Doris user dave holds SELECT_PRIV ON internal.hr.* through role analysts, outside GrantForge, which gives select on internal.hr"
                        + " although GrantForge policies deny part of it; the agent cannot take it away",
                "Doris user dave holds SELECT_PRIV ON internal.hr.salaries directly, outside GrantForge, which gives select on internal.hr.salaries"
                        + " although GrantForge policy 1 denies it; the agent cannot take it away",
                "Doris user dave holds USAGE_PRIV ON WORKLOAD GROUP etl directly, outside GrantForge, which gives usage on etl although GrantForge"
                        + " policy 2 denies it; the agent cannot take it away");
        assertThat(conflicts.check(state, policies.snapshot(), Map.of(), user -> "grantforge_" + user, Instant.now())).hasSize(4);
    }

    @Test
    void reportsColumnsAndPermissiveRowPoliciesOutsideGrantForge()
    {
        DorisSnapshots policies = new DorisSnapshots().allow(List.of("dave"), List.of("select"), column("sales", "orders", "id"))
                .deny(List.of("dave"), List.of("select"), column("sales", "orders", "card"))
                .filter(List.of("dave"), "region = 'EU'", "sales", "orders");
        Account dave = new Account("dave", List.of(DAVE), Set.of("analysts"), Map.of(DAVE, Set.of("analysts")),
                Map.of(DAVE, Map.of(ORDERS, Set.of("id", "card"))));
        Plan plan = new Plan(List.of(), Map.of(ORDERS, new TreeSet<>(List.of("id"))), Map.of(ORDERS, new Plan.Filter("region = 'EU'", 3)));
        ServerState state = state(dave, Map.of(), Set.of(), Map.of(new ServerState.RowPolicyKey("wide", ORDERS),
                new ServerState.RowPolicy(false, "(1 = 1)", null, "analysts"), new ServerState.RowPolicyKey("narrow", ORDERS),
                new ServerState.RowPolicy(true, "(1 = 0)", DAVE, null)));
        List<NativeConflicts.Conflict> found = new NativeConflicts().check(state, policies.snapshot(), Map.of("dave", plan),
                user -> "grantforge_" + user, Instant.now());
        assertThat(found).extracting(NativeConflicts.Conflict::grant).containsExactlyInAnyOrder("ADMIN_PRIV or NODE_PRIV",
                "SELECT_PRIV(card) ON internal.sales.orders", "row policy wide USING (1 = 1)");
    }

    @Test
    void reportsAtMostTwentyNewConflictsOneByOne()
    {
        DorisSnapshots policies = new DorisSnapshots().deny(List.of("dave"), List.of("load"), "catalog", "internal", "database", "*", "table", "*");
        Set<Privilege> direct = new HashSet<>();
        IntStream.range(0, 30).forEach(index -> direct.add(Privilege.table("LOAD_PRIV", new TableRef("internal", "sales", "t" + index))));
        Account dave = new Account("dave", List.of(DAVE), Set.of(), Map.of(), Map.of());
        assertThat(new NativeConflicts().check(state(dave, Map.of(), direct, Map.of()), policies.snapshot(), Map.of(), user -> "grantforge_" + user,
                Instant.now())).hasSize(31);
    }
}
