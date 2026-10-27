// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class SyncTest
{
    @TempDir
    Path directory;

    private final FakePostgreSql postgres = ServerStateTest.server();
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();

    private Sync sync() throws IOException
    {
        return new Sync(postgres, AgentConfig.parse(AgentConfigTest.minimal(directory), "1"), snapshot::get, Clock.systemUTC());
    }

    private static String[] column(String database, String schema, String table, String column)
    {
        return new String[] {"database", database, "schema", schema, "table", table, "column", column};
    }

    @Test
    void leavesPostgreSqlAloneWithoutPolicies() throws IOException
    {
        Sync sync = sync();
        assertThat(sync.run()).isNull();
        assertThat(sync.run()).isNull();
        assertThat(postgres.queried).isEmpty();
        assertThat(sync.inventoryOf("sales").schemas()).isEmpty();
        assertThat(sync.groupsOf("carol")).isEmpty();
    }

    @Test
    void appliesThePoliciesAndRemembersWhatTheServerHeld() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new PostgreSqlSnapshots().allow(List.of("carol"), List.of("select"), column("sales", "crm", "orders", "*")).snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run(), "outcome");
        assertThat(outcome.users()).as("alice, bob, carol and etl").isEqualTo(4);
        assertThat(postgres.executed).contains("sales: GRANT SELECT ON TABLE \"crm\".\"orders\" TO \"grantforge_carol\"");
        assertThat(sync.groupsOf("carol")).containsExactlyInAnyOrder("analysts", "readers");
        assertThat(sync.groupsOf("nobody")).isEmpty();
        assertThat(sync.inventoryOf("sales").columns(ServerStateTest.ORDERS)).containsExactly("id", "region", "card");
        assertThat(sync.inventoryOf("elsewhere").schemas()).isEmpty();
        assertThat(sync.role("carol")).isEqualTo("grantforge_carol");
        assertThat(sync.run()).as("the same version again").isNotNull();
    }

    @Test
    void grantsNothingForAServiceNotInUseOrOfAnotherType() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*")).disabled().snapshot());
        sync.run();
        assertThat(postgres.executed).contains("postgres: DROP ROLE IF EXISTS \"grantforge_alice\"", "postgres: DROP ROLE IF EXISTS \"grantforge_stale\"");
        postgres.executed.clear();
        snapshot.set(new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*", "*")).serviceType("hive")
                .snapshot());
        sync.run();
        sync.run();
        assertThat(postgres.executed).contains("postgres: DROP ROLE IF EXISTS \"grantforge_alice\"");
    }
}
