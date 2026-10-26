// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcilerTest
{
    private static final TableRef ORDERS = ServerStateTest.ORDERS;
    private static final TableRef REGIONS = new TableRef("crm", "regions");
    private static final String CREATE_ALICE = "CREATE POLICY \"grantforge_alice\" ON \"crm\".\"orders\" AS RESTRICTIVE FOR SELECT TO \"grantforge_alice\"";

    private final FakePostgreSql postgres = ServerStateTest.server();
    private final Reconciler reconciler = new Reconciler(postgres, "postgres", "grantforge_");

    private static Plan reading(TableRef table)
    {
        return new Plan(Map.of("sales", Set.of(Privilege.database("sales", "CONNECT"), Privilege.schema("crm", "USAGE"), Privilege.table(table,
                "SELECT"))), Map.of(), List.of());
    }

    private static Plan filtering(TableRef table, String expression)
    {
        return new Plan(Map.of("sales", Set.of(Privilege.database("sales", "CONNECT"), Privilege.schema("crm", "USAGE"), Privilege.table(table,
                "SELECT"))), Map.of("sales", Map.of(table, new Plan.Filter(expression, 7))), List.of());
    }

    private static Function<Account, Plan> only(String user, Plan plan)
    {
        return account -> account.name().equals(user) ? plan : Plan.NOTHING;
    }

    /** Answers the read-back of a created row security policy. */
    private void createdAs(boolean permissive, String command, List<String> roles, String using)
    {
        postgres.answer("sales", "FROM pg_policy p WHERE p.polrelid = ?::regclass", Row.of("permissive", permissive, "command", command, "roles", roles,
                "using", using));
    }

    @Test
    void keepsOneRolePerUserHoldingWhatItsPlanGrants()
    {
        ServerState state = ServerStateTest.read(postgres);
        Reconciler.Outcome outcome = reconciler.reconcile(state, only("alice", reading(REGIONS)));
        assertThat(postgres.executed).containsExactly(
                "sales: DROP POLICY \"grantforge_alice\" ON \"crm\".\"orders\"", "sales: DROP POLICY \"grantforge\" ON \"crm\".\"orders\"",
                "sales: ALTER TABLE \"crm\".\"orders\" DISABLE ROW LEVEL SECURITY",
                "sales: REVOKE SELECT ON TABLE \"crm\".\"orders\" FROM \"grantforge_alice\"",
                "sales: GRANT SELECT ON TABLE \"crm\".\"regions\" TO \"grantforge_alice\"",
                "sales: REVOKE USAGE ON SEQUENCE \"crm\".\"orders_id_seq\" FROM \"grantforge_stale\"",
                "hr: DROP OWNED BY \"grantforge_stale\"", "sales: DROP OWNED BY \"grantforge_stale\"", "postgres: DROP ROLE IF EXISTS \"grantforge_stale\"");
        assertThat(outcome).isEqualTo(new Reconciler.Outcome(3, 0, 1, 3, 2, 0));
        assertThat(outcome.changed()).isTrue();
    }

    @Test
    void createsTheRoleOfANewUserAndMakesItAMember()
    {
        ServerState state = ServerStateTest.read(postgres);
        reconciler.reconcile(state, only("carol", reading(ORDERS)));
        assertThat(postgres.executed).startsWith("postgres: CREATE ROLE \"grantforge_carol\" NOLOGIN",
                "postgres: COMMENT ON ROLE \"grantforge_carol\" IS 'GrantForge agent role of user carol'",
                "postgres: GRANT \"grantforge_carol\" TO \"carol\" WITH INHERIT TRUE");
        assertThat(postgres.executed).contains("sales: GRANT CONNECT ON DATABASE \"sales\" TO \"grantforge_carol\"",
                "postgres: DROP ROLE IF EXISTS \"grantforge_alice\"");
    }

    @Test
    void membershipsInheritThePrivilegesOrAreReported()
    {
        FakePostgreSql older = ServerStateTest.server().answer("server_version_num')::int AS version", Row.of("self", "grantforge", "version", 150019))
                .answer("FROM pg_auth_members", Row.of("member", "alice", "role", "grantforge_alice", "inherit", false));
        new Reconciler(older, "postgres", "grantforge_").reconcile(ServerStateTest.read(older), only("alice", reading(ORDERS)));
        assertThat(older.executed).noneMatch(statement -> statement.contains("TO \"alice\""));

        FakePostgreSql newer = ServerStateTest.server().answer("FROM pg_auth_members", Row.of("member", "alice", "role", "grantforge_alice",
                "inherit", false));
        new Reconciler(newer, "postgres", "grantforge_").reconcile(ServerStateTest.read(newer), only("alice", reading(ORDERS)));
        assertThat(newer.executed).contains("postgres: GRANT \"grantforge_alice\" TO \"alice\" WITH INHERIT TRUE");
    }

    @Test
    void putsARowFilterInPlaceOnceAndKeepsItWhileItIsAsLeft()
    {
        createdAs(false, "r", List.of("grantforge_alice"), "(region = 'EU'::text)");
        reconciler.reconcile(ServerStateTest.read(postgres), only("alice", filtering(ORDERS, "region = 'EU'")));
        assertThat(postgres.executed).contains("sales: DROP POLICY IF EXISTS \"grantforge_alice\" ON \"crm\".\"orders\"",
                "sales: " + CREATE_ALICE + " USING (region = 'EU')");
        assertThat(postgres.executed).noneMatch(statement -> statement.contains("ENABLE ROW LEVEL SECURITY"));
        postgres.executed.clear();
        Reconciler.Outcome again = reconciler.reconcile(ServerStateTest.read(postgres), only("alice", filtering(ORDERS, "region = 'EU'")));
        assertThat(again.rowPolicies()).isZero();
        assertThat(postgres.executed).noneMatch(statement -> statement.contains("POLICY"));
        reconciler.reconcile(ServerStateTest.read(postgres), only("alice", filtering(ORDERS, "region = 'US'")));
        assertThat(postgres.executed).contains("sales: " + CREATE_ALICE + " USING (region = 'US')");
    }

    @Test
    void aRowFilterRefusedOrBindingOthersShowsNoRows()
    {
        postgres.fail("USING (no_such_column", "42703");
        createdAs(false, "r", List.of("grantforge_alice"), "false");
        reconciler.reconcile(ServerStateTest.read(postgres), only("alice", filtering(ORDERS, "no_such_column = 1")));
        assertThat(postgres.executed).contains("sales: " + CREATE_ALICE + " USING (false)");

        FakePostgreSql widening = ServerStateTest.server();
        widening.answer("sales", "FROM pg_policy p WHERE p.polrelid = ?::regclass", parameters -> List.of(Row.of("permissive", false, "command", "r",
                "roles", widening.executed.stream().anyMatch(statement -> statement.endsWith("USING (false)")) ? List.of("grantforge_alice")
                        : List.of("grantforge_alice", ""), "using", "true")));
        new Reconciler(widening, "postgres", "grantforge_").reconcile(ServerStateTest.read(widening), only("alice", filtering(ORDERS, "true")));
        assertThat(widening.executed).containsSubsequence("sales: " + CREATE_ALICE + " USING (true)",
                "sales: DROP POLICY IF EXISTS \"grantforge_alice\" ON \"crm\".\"orders\"", "sales: " + CREATE_ALICE + " USING (false)");

        FakePostgreSql vanishing = ServerStateTest.server();
        new Reconciler(vanishing, "postgres", "grantforge_").reconcile(ServerStateTest.read(vanishing), only("alice", filtering(ORDERS, "true")));
        assertThat(vanishing.executed).as("a policy PostgreSQL does not show is replaced too").contains("sales: " + CREATE_ALICE + " USING (false)");
    }

    @Test
    void turnsRowSecurityOnWithAPolicyForEverybodyAndOffAgain()
    {
        TableRef items = new TableRef("crm", "items");
        createdAs(false, "r", List.of("grantforge_alice"), "true");
        reconciler.reconcile(ServerStateTest.read(postgres), only("alice", filtering(items, "true")));
        assertThat(postgres.executed).containsSubsequence("sales: ALTER TABLE \"crm\".\"items\" ENABLE ROW LEVEL SECURITY",
                "sales: CREATE POLICY \"grantforge\" ON \"crm\".\"items\" AS PERMISSIVE FOR ALL TO PUBLIC USING (true) WITH CHECK (true)");

        postgres.executed.clear();
        postgres.answer("sales", "LEFT JOIN pg_policy p", ServerStateTest.policy("orders", true, "grantforge", true, "*", List.of(""), "true"),
                ServerStateTest.policy("orders", true, "grantforge_alice", false, "r", List.of("grantforge_alice"), "true"),
                ServerStateTest.policy("items", true, "grantforge_alice", false, "r", List.of("grantforge_alice"), "true"));
        reconciler.reconcile(ServerStateTest.read(postgres), only("alice", filtering(items, "true")));
        assertThat(postgres.executed).as("its policy for everybody went missing, so it is put back").contains(
                "sales: CREATE POLICY \"grantforge\" ON \"crm\".\"items\" AS PERMISSIVE FOR ALL TO PUBLIC USING (true) WITH CHECK (true)");

        postgres.executed.clear();
        reconciler.reconcile(ServerStateTest.read(postgres), only("alice", reading(ORDERS)));
        assertThat(postgres.executed).contains("sales: DROP POLICY \"grantforge_alice\" ON \"crm\".\"orders\"",
                "sales: DROP POLICY \"grantforge\" ON \"crm\".\"orders\"", "sales: ALTER TABLE \"crm\".\"orders\" DISABLE ROW LEVEL SECURITY");
    }

    @Test
    void leavesRowSecurityOnWhereTheTableHasPoliciesOfItsOwn()
    {
        postgres.answer("sales", "LEFT JOIN pg_policy p", ServerStateTest.policy("orders", true, "grantforge", true, "*", List.of(""), "true"),
                ServerStateTest.policy("orders", true, "theirs", true, "r", List.of(""), "true"));
        reconciler.reconcile(ServerStateTest.read(postgres), only("alice", reading(ORDERS)));
        assertThat(postgres.executed).noneMatch(statement -> statement.contains("ROW LEVEL SECURITY") || statement.contains("DROP POLICY"));
    }

    @Test
    void aUserWhosePlanFailsKeepsItsRoleAsItIs()
    {
        Reconciler.Outcome outcome = reconciler.reconcile(ServerStateTest.read(postgres), account -> {
            throw new IllegalStateException("broken");
        });
        assertThat(outcome.failed()).isEqualTo(3);
        assertThat(postgres.executed).as("only the role nobody keeps goes").noneMatch(statement -> statement.contains("grantforge_alice"))
                .contains("postgres: DROP ROLE IF EXISTS \"grantforge_stale\"");
    }

    @Test
    void refusalsAreReportedAndAServerThatCannotBeReachedStopsTheSync()
    {
        postgres.fail("CREATE ROLE \"grantforge_carol\"", "42501");
        Reconciler.Outcome refused = reconciler.reconcile(ServerStateTest.read(postgres), only("carol", reading(ORDERS)));
        assertThat(refused.failed()).isEqualTo(1);
        assertThat(postgres.executed).noneMatch(statement -> statement.contains("grantforge_carol"));

        FakePostgreSql rolling = ServerStateTest.server().fail("sales: GRANT SELECT ON TABLE \"crm\".\"regions\"", "42P01");
        Reconciler.Outcome rolled = new Reconciler(rolling, "postgres", "grantforge_").reconcile(ServerStateTest.read(rolling),
                only("alice", reading(REGIONS)));
        assertThat(rolled.failed()).isEqualTo(1);
        assertThat(rolling.rolledBack).contains("sales: REVOKE SELECT ON TABLE \"crm\".\"orders\" FROM \"grantforge_alice\"");

        FakePostgreSql dropping = ServerStateTest.server().fail("DROP ROLE IF EXISTS \"grantforge_stale\"", "2BP01");
        assertThat(new Reconciler(dropping, "postgres", "grantforge_").reconcile(ServerStateTest.read(dropping), only("alice", reading(ORDERS)))
                .failed()).isEqualTo(1);

        for (String fragment : List.of("CREATE ROLE", "sales: GRANT", "DROP ROLE")) {
            FakePostgreSql down = ServerStateTest.server().fail(fragment, "08006");
            Function<Account, Plan> plans = only("carol", reading(REGIONS));
            assertThatThrownBy(() -> new Reconciler(down, "postgres", "grantforge_").reconcile(ServerStateTest.read(down), plans))
                    .as(fragment).isInstanceOf(PostgreSqlFailure.class);
        }
    }

    @Test
    void namesRolesAfterTheirUsersWithinPostgreSqlsLimit()
    {
        assertThat(reconciler.role("alice")).isEqualTo("grantforge_alice");
        assertThat(reconciler.everybody()).isEqualTo("grantforge");
        String fits = "u".repeat(Reconciler.MAX_NAME - "grantforge_".length());
        assertThat(reconciler.role(fits)).isEqualTo("grantforge_" + fits);
        String role = reconciler.role(fits + "x");
        assertThat(role.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(Reconciler.MAX_NAME);
        assertThat(role).startsWith("grantforge_uuu").matches(".*_[0-9a-f]{8}").isNotEqualTo(reconciler.role(fits + "y"));
        String wide = reconciler.role("用户".repeat(30));
        assertThat(wide.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(Reconciler.MAX_NAME);
        assertThat(wide).startsWith("grantforge_用户");
    }
}
