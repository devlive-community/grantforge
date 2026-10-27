// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

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

    private final FakeDoris doris = ServerStateTest.cluster();
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();

    private Sync sync(String... more) throws IOException
    {
        var values = AgentConfigTest.minimal(directory);
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new Sync(doris, AgentConfig.parse(values, "1"), snapshot::get, Clock.systemUTC());
    }

    @Test
    void leavesDorisAloneWithoutPolicies() throws IOException
    {
        Sync sync = sync();
        assertThat(sync.run()).isNull();
        assertThat(doris.parameters).isEmpty();
        assertThat(sync.columnsOf(ServerStateTest.ORDERS)).isEmpty();
        assertThat(sync.groupsOf("alice")).isEmpty();
    }

    @Test
    void appliesThePoliciesAndRemembersWhatTheClusterHeld() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new DorisSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "internal", "database", "sales", "table", "*",
                "column", "*").allow(List.of("alice"), List.of("create_database"), "catalog", "internal").snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run(), "outcome");
        assertThat(outcome.users()).as("alice and etl").isEqualTo(2);
        assertThat(doris.executed).contains("REVOKE CREATE_PRIV ON `internal`.*.* FROM ROLE 'grantforge_alice'",
                "GRANT 'grantforge_alice' TO 'alice'@'10.%'");
        assertThat(sync.groupsOf("alice")).containsExactly("analysts");
        assertThat(sync.columnsOf(ServerStateTest.ORDERS)).containsExactly("id", "Region");
        assertThat(sync.role("alice")).isEqualTo("grantforge_alice");
        assertThat(sync.summary(outcome)).startsWith("2 users, 0 roles created");
        assertThat(sync.changed(outcome)).isTrue();
    }

    @Test
    void looksAfterTheCatalogsItIsGivenByName() throws IOException
    {
        Sync sync = sync("grantforge.doris.catalogs", "hive");
        snapshot.set(new DorisSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "internal", "database", "sales", "table", "orders",
                "column", "id").snapshot());
        sync.run();
        assertThat(doris.executed).noneMatch(sql -> sql.startsWith("GRANT SELECT_PRIV(`id`)"));
    }

    @Test
    void grantsNothingForAServiceNotInUse() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new DorisSnapshots().allow(List.of("alice"), List.of("select"), "catalog", "internal").disabled().snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run(), "outcome");
        assertThat(doris.executed).startsWith("DROP ROLE IF EXISTS `grantforge_alice`");
        assertThat(sync.summary(new Reconciler.Outcome(1, 0, 1, 0, 1, 2))).endsWith(", 2 users failed");
        assertThat(outcome.dropped()).isEqualTo(1);
    }
}
