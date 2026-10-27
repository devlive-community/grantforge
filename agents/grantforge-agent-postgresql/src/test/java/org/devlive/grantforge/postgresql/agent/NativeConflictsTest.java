// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NativeConflictsTest
{
    private static String[] column(String database, String schema, String table, String column)
    {
        return new String[] {"database", database, "schema", schema, "table", table, "column", column};
    }

    @Test
    void findsPrivilegesOutsideGrantForgeThatGiveWhatPoliciesDeny()
    {
        FakePostgreSql postgres = ServerStateTest.server().answer("sales", "aclexplode(c.relacl)",
                ServerStateTest.acl("COLUMN", "crm", "orders", "card", "SELECT", "bob", true),
                ServerStateTest.acl("TABLE", "crm", "orders", null, "SELECT", "analysts", true),
                ServerStateTest.acl("TABLE", "crm", "orders", null, "REFERENCES", "bob", true),
                ServerStateTest.acl("SCHEMA", null, "crm", null, "CREATE", "alice", true),
                ServerStateTest.acl("SEQUENCE", "crm", "orders_id_seq", null, "USAGE", "bob", true));
        ServerState state = ServerStateTest.read(postgres);
        PostgreSqlSnapshots policies = new PostgreSqlSnapshots();
        long card = policies.nextId();
        policies.deny(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "card"))
                .allow(List.of("carol"), List.of("select"), column("sales", "crm", "orders", "*"))
                .deny(List.of("carol"), List.of("select"), column("sales", "crm", "orders", "card"));
        long salaries = policies.nextId();
        policies.deny(List.of("carol"), List.of("select"), column("hr", "public", "salaries", "*"));
        long temporary = policies.nextId();
        policies.deny(List.of("alice"), List.of("temporary"), "database", "sales")
                .deny(List.of("alice"), List.of("create"), "database", "sales", "schema", "crm");

        List<NativeConflicts.Conflict> found = new NativeConflicts().check(state, policies.snapshot(), Instant.now());
        assertThat(found).extracting(NativeConflicts.Conflict::message).containsExactlyInAnyOrder(
                "PostgreSQL user bob holds SELECT (card) ON TABLE crm.orders, outside GrantForge, which gives select on sales.crm.orders.card although"
                        + " GrantForge policy " + card + " denies it; the agent cannot take it away",
                "PostgreSQL user carol holds SELECT ON TABLE crm.orders through role analysts, outside GrantForge, which gives select on"
                        + " sales.crm.orders although GrantForge policies deny part of it; the agent cannot take it away",
                "PostgreSQL user carol holds SELECT ON TABLE public.salaries, outside GrantForge, which gives select on hr.public.salaries although"
                        + " GrantForge policy " + salaries + " denies it; the agent cannot take it away",
                "PostgreSQL user alice holds TEMPORARY ON DATABASE sales through PUBLIC, outside GrantForge, which gives temporary on sales although"
                        + " GrantForge policy " + temporary + " denies it; the agent cannot take it away",
                "PostgreSQL user alice holds CREATE ON SCHEMA crm, outside GrantForge, which gives create on sales.crm although GrantForge policy "
                        + (temporary + 1) + " denies it; the agent cannot take it away");
    }

    @Test
    void ownersHoldEveryPrivilegeOfTheirTables()
    {
        FakePostgreSql postgres = ServerStateTest.server().answer("hr", "FROM pg_namespace n LEFT JOIN pg_class c",
                Row.of("schema", "public", "name", "salaries", "kind", "r", "owner", "alice", "column", "amount"));
        ServerState state = ServerStateTest.read(postgres);
        PostgreSqlSnapshots policies = new PostgreSqlSnapshots().deny(List.of("alice"), List.of("delete"), "database", "hr", "schema", "public",
                "table", "salaries");
        List<NativeConflicts.Conflict> found = new NativeConflicts().check(state, policies.snapshot(), Instant.now());
        assertThat(found).singleElement().satisfies(conflict -> {
            assertThat(conflict.message()).startsWith("PostgreSQL user alice owns TABLE public.salaries, outside GrantForge, which gives delete");
            assertThat(conflict.through()).isNull();
        });
    }

    @Test
    void warnsOfEachConflictOnceAndOfManyInAWord()
    {
        List<Row> grants = new ArrayList<>();
        for (int index = 0; index < NativeConflicts.MAX_REPORTED + 5; index++) {
            grants.add(ServerStateTest.acl("COLUMN", "crm", "orders", "c" + index, "SELECT", "bob", true));
        }
        FakePostgreSql postgres = ServerStateTest.server().answer("sales", "aclexplode(c.relacl)", grants.toArray(new Row[0]));
        ServerState state = ServerStateTest.read(postgres);
        PostgreSqlSnapshots policies = new PostgreSqlSnapshots().deny(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "*"));
        NativeConflicts conflicts = new NativeConflicts();
        assertThat(conflicts.check(state, policies.snapshot(), Instant.now())).hasSize(NativeConflicts.MAX_REPORTED + 5);
        assertThat(conflicts.check(state, policies.snapshot(), Instant.now())).as("found again, warned of once").hasSize(NativeConflicts.MAX_REPORTED + 5);
        assertThat(conflicts.check(ServerStateTest.read(ServerStateTest.server()), new PostgreSqlSnapshots().snapshot(), Instant.now())).isEmpty();
    }
}
