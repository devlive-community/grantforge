// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

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

    private final FakeClickHouse clickhouse = ServerStateTest.server();
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();

    private Sync sync() throws IOException
    {
        return new Sync(clickhouse, AgentConfig.parse(AgentConfigTest.minimal(directory), "1"), snapshot::get, Clock.systemUTC());
    }

    @Test
    void leavesClickHouseAloneWithoutPolicies() throws IOException
    {
        Sync sync = sync();
        assertThat(sync.run()).isNull();
        assertThat(sync.run()).isNull();
        assertThat(clickhouse.parameters).isEmpty();
        assertThat(sync.columnsOf(new TableRef("sales", "orders"))).isEmpty();
        assertThat(sync.groupsOf("alice")).isEmpty();
    }

    @Test
    void appliesThePoliciesAndRemembersWhatTheServerHeld() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), "database", "sales", "table", "*", "column", "*").snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run(), "outcome");
        assertThat(outcome.users()).as("alice, carol and etl").isEqualTo(3);
        assertThat(clickhouse.executed).contains("GRANT SELECT ON \"sales\".* TO \"grantforge_alice\" WITH REPLACE OPTION");
        assertThat(sync.groupsOf("alice")).containsExactlyInAnyOrder("analysts", "readers");
        assertThat(sync.columnsOf(new TableRef("sales", "orders"))).containsExactly("id", "region");
        assertThat(sync.role("alice")).isEqualTo("grantforge_alice");
        assertThat(sync.run()).isNotNull();
    }

    @Test
    void grantsNothingForAServiceNotInUseOrOfAnotherType() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), "database", "sales", "table", "*", "column", "*")
                .disabled().snapshot());
        sync.run();
        assertThat(clickhouse.executed).containsExactly("DROP ROLE IF EXISTS \"grantforge_alice\"",
                "DROP ROW POLICY IF EXISTS \"grantforge_alice\" ON \"sales\".\"orders\"");
        clickhouse.executed.clear();
        snapshot.set(new ClickHouseSnapshots().allow(List.of("alice"), List.of("select"), "database", "sales", "table", "*", "column", "*")
                .serviceType("hive").snapshot());
        sync.run();
        sync.run();
        assertThat(clickhouse.executed).startsWith("DROP ROLE IF EXISTS \"grantforge_alice\"");
    }
}
