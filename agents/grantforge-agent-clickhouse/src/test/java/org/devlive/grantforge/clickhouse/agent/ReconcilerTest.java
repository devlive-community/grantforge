// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcilerTest
{
    private static final Inventory INVENTORY = new Inventory(Map.of("sales", List.of("orders")), database -> Map.of());
    private static final TableRef ORDERS = new TableRef("sales", "orders");
    private static final Plan SELECT = new Plan(List.of(Grant.database("select", "sales")), Map.of());

    private final FakeClickHouse clickhouse = new FakeClickHouse()
            .answer("system.grants WHERE startsWith", Row.of("role_name", "grantforge_alice", "access_type", "SELECT", "database", "sales"));
    private final Reconciler reconciler = new Reconciler(clickhouse, "grantforge_");

    private static Account account(String name, String... granted)
    {
        return new Account(name, Set.of(), Set.of(granted), true, List.of(), List.of());
    }

    private static ServerState state(List<Account> accounts, Set<String> roles, Map<String, String> privileges,
            Map<ServerState.RowPolicyKey, ServerState.RowPolicy> policies)
    {
        return new ServerState("grantforge", accounts, List.of(), roles, privileges, policies, List.of(), INVENTORY);
    }

    private static Plan filtered(String expression)
    {
        return new Plan(List.of(Grant.database("select", "sales")), Map.of(ORDERS, new Plan.Filter(expression, 9)));
    }

    private void readsBack(String filter, boolean toAll)
    {
        clickhouse.answer("short_name = {name:String}", Row.of("select_filter", filter, "is_restrictive", 0, "apply_to_all", toAll ? 1 : 0,
                "apply_to_list", toAll ? List.of() : List.of("grantforge_alice"), "apply_to_except", List.of()));
    }

    private static Map<ServerState.RowPolicyKey, ServerState.RowPolicy> policy(String filter)
    {
        return Map.of(new ServerState.RowPolicyKey("grantforge_alice", ORDERS), new ServerState.RowPolicy(filter, false, false,
                List.of("grantforge_alice"), List.of()));
    }

    @Test
    void createsTheRoleGrantsItAndThenLeavesItAlone()
    {
        Reconciler.Outcome first = reconciler.reconcile(state(List.of(account("alice")), Set.of(), Map.of(), Map.of()), account -> SELECT);
        assertThat(clickhouse.executed).containsExactly("CREATE ROLE IF NOT EXISTS \"grantforge_alice\"",
                "GRANT SELECT ON \"sales\".* TO \"grantforge_alice\" WITH REPLACE OPTION", "GRANT \"grantforge_alice\" TO \"alice\"");
        assertThat(first).isEqualTo(new Reconciler.Outcome(1, 1, 0, 0, 0));
        assertThat(first.changed()).isTrue();

        clickhouse.executed.clear();
        Map<String, String> held = ServerState.privileges(clickhouse, "grantforge_");
        Reconciler.Outcome second = reconciler.reconcile(state(List.of(account("alice", "grantforge_alice")), Set.of("grantforge_alice"), held,
                Map.of()), account -> SELECT);
        assertThat(clickhouse.executed).isEmpty();
        assertThat(second.changed()).isFalse();
        assertThat(reconciler.role("bob")).isEqualTo("grantforge_bob");
    }

    @Test
    void undoesChangesToItsRoleMadeByHand()
    {
        reconciler.reconcile(state(List.of(account("alice")), Set.of(), Map.of(), Map.of()), account -> SELECT);
        clickhouse.executed.clear();
        reconciler.reconcile(state(List.of(account("alice", "grantforge_alice")), Set.of("grantforge_alice"), Map.of("grantforge_alice", "changed"),
                Map.of()), account -> SELECT);
        assertThat(clickhouse.executed).containsExactly("GRANT SELECT ON \"sales\".* TO \"grantforge_alice\" WITH REPLACE OPTION");
    }

    @Test
    void makesItsRoleADefaultRole()
    {
        Account except = new Account("alice", Set.of(), Set.of("grantforge_alice"), true, List.of(), List.of("grantforge_alice", "other"));
        Account exceptOnly = new Account("bob", Set.of(), Set.of("grantforge_bob"), true, List.of(), List.of("grantforge_bob"));
        Account listed = new Account("carol", Set.of(), Set.of("grantforge_carol"), false, List.of("x"), List.of());
        reconciler.reconcile(state(List.of(except, exceptOnly, listed), Set.of("grantforge_alice", "grantforge_bob", "grantforge_carol"), Map.of(),
                Map.of()), account -> SELECT);
        assertThat(clickhouse.executed).contains("SET DEFAULT ROLE ALL EXCEPT \"other\" TO \"alice\"", "SET DEFAULT ROLE ALL TO \"bob\"",
                "SET DEFAULT ROLE \"x\", \"grantforge_carol\" TO \"carol\"");
    }

    @Test
    void dropsTheRolesAndRowPoliciesNobodyNeeds()
    {
        Reconciler.Outcome outcome = reconciler.reconcile(state(List.of(account("alice", "grantforge_alice")), Set.of("grantforge_alice",
                "grantforge_gone"), Map.of(), policy("x")), null);
        assertThat(clickhouse.executed.subList(0, 2)).containsExactlyInAnyOrder("DROP ROLE IF EXISTS \"grantforge_alice\"",
                "DROP ROLE IF EXISTS \"grantforge_gone\"");
        assertThat(clickhouse.executed).hasSize(3).last().isEqualTo("DROP ROW POLICY IF EXISTS \"grantforge_alice\" ON \"sales\".\"orders\"");
        assertThat(outcome).isEqualTo(new Reconciler.Outcome(1, 0, 2, 1, 0));
    }

    @Test
    void putsAFilterInPlaceBeforeThePrivilegesItNarrowsAndKeepsIt()
    {
        readsBack("region = 'EU'", false);
        Account alice = account("alice", "grantforge_alice");
        reconciler.reconcile(state(List.of(alice), Set.of("grantforge_alice"), Map.of(), Map.of()), account -> filtered("region = 'EU'"));
        assertThat(clickhouse.executed).containsExactly("CREATE ROW POLICY OR REPLACE \"grantforge_alice\" ON \"sales\".\"orders\" AS PERMISSIVE FOR SELECT"
                + " USING (region = 'EU') TO \"grantforge_alice\"", "GRANT SELECT ON \"sales\".* TO \"grantforge_alice\" WITH REPLACE OPTION");

        clickhouse.executed.clear();
        Map<String, String> held = ServerState.privileges(clickhouse, "grantforge_");
        reconciler.reconcile(state(List.of(alice), Set.of("grantforge_alice"), held, policy("region = 'EU'")), account -> filtered("region = 'EU'"));
        assertThat(clickhouse.executed).isEmpty();

        readsBack("region = 'US'", false);
        Reconciler.Outcome changed = reconciler.reconcile(state(List.of(alice), Set.of("grantforge_alice"), held, policy("region = 'EU'")),
                account -> filtered("region = 'US'"));
        assertThat(changed.rowPolicies()).isEqualTo(1);
        assertThat(clickhouse.executed).singleElement().asString().contains("USING (region = 'US')");

        clickhouse.executed.clear();
        reconciler.reconcile(state(List.of(alice), Set.of("grantforge_alice"), Map.of(), policy("region = 'US'")), account -> SELECT);
        assertThat(clickhouse.executed).as("the filter goes after the privileges it narrowed").containsExactly(
                "GRANT SELECT ON \"sales\".* TO \"grantforge_alice\" WITH REPLACE OPTION",
                "DROP ROW POLICY IF EXISTS \"grantforge_alice\" ON \"sales\".\"orders\"");
    }

    @Test
    void replacesAFilterThatAppliesToOthersOrIsRefusedByOneThatShowsNoRows()
    {
        readsBack("1", true);
        Account alice = account("alice", "grantforge_alice");
        reconciler.reconcile(state(List.of(alice), Set.of("grantforge_alice"), Map.of(), Map.of()), account -> filtered("1) TO ALL --"));
        assertThat(clickhouse.executed).anySatisfy(sql -> assertThat(sql).contains("USING (0) TO \"grantforge_alice\""));

        clickhouse.executed.clear();
        clickhouse.fail("USING (no_such", 47);
        clickhouse.answer("short_name = {name:String}", parameters -> List.of());
        reconciler.reconcile(state(List.of(alice), Set.of("grantforge_alice"), Map.of(), Map.of()), account -> filtered("no_such = 1"));
        assertThat(clickhouse.executed).first().asString().contains("USING (0)");

        clickhouse.fail("USING (cut", ClickHouseFailure.UNREACHABLE);
        assertThatThrownBy(() -> reconciler.reconcile(state(List.of(alice), Set.of("grantforge_alice"), Map.of(), Map.of()),
                account -> filtered("cut = 1"))).isInstanceOf(ClickHouseFailure.class);
    }

    @Test
    void keepsTheRoleOfAUserItCannotPlanAndGoesOnWhenClickHouseRefusesOne()
    {
        Function<Account, Plan> plans = account -> {
            if ("bob".equals(account.name())) {
                throw new IllegalStateException("broken");
            }
            return SELECT;
        };
        clickhouse.fail("TO \"grantforge_carol\" WITH REPLACE", 497);
        Map<String, String> none = new HashMap<>();
        Reconciler.Outcome outcome = reconciler.reconcile(state(List.of(account("alice"), account("bob", "grantforge_bob"), account("carol")),
                Set.of("grantforge_bob"), none, Map.of()), plans);
        assertThat(clickhouse.executed).doesNotContain("DROP ROLE IF EXISTS \"grantforge_bob\"").contains("GRANT \"grantforge_alice\" TO \"alice\"");
        assertThat(outcome.failed()).isEqualTo(2);
        assertThat(outcome.roles()).isEqualTo(1);

        clickhouse.fail("CREATE ROLE", ClickHouseFailure.UNREACHABLE);
        assertThatThrownBy(() -> reconciler.reconcile(state(List.of(account("dave")), Set.of(), none, Map.of()), account -> SELECT))
                .isInstanceOf(ClickHouseFailure.class);
    }

    @Test
    void anEmptyPlanNeedsNoRole()
    {
        reconciler.reconcile(state(List.of(account("alice")), Set.of(), Map.of(), Map.of()), account -> Plan.NOTHING);
        assertThat(clickhouse.executed).isEmpty();
    }
}
