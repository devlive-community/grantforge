// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

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

    private final FakeMongo mongo = ServerStateTest.deployment();
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();

    private Sync sync(String... more) throws IOException
    {
        var values = AgentConfigTest.minimal(directory);
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new Sync(mongo, AgentConfig.parse(values, "1"), "grantforge", snapshot::get, Clock.systemUTC());
    }

    @Test
    void leavesMongoDbAloneWithoutPolicies() throws IOException
    {
        Sync sync = sync();
        assertThat(sync.run()).isNull();
        assertThat(mongo.changes).isEmpty();
        assertThat(sync.inventory()).isNull();
        assertThat(sync.groupsOf("alice")).isEmpty();
    }

    @Test
    void appliesThePoliciesAndRemembersWhatTheDeploymentHeld() throws IOException
    {
        Sync sync = sync("grantforge.mongodb.unmanaged.users", "etl");
        snapshot.set(new MongoSnapshots().allow(List.of("bob"), List.of("read"), "database", "sales", "collection", "*").snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run(), "outcome");
        assertThat(mongo.changes).contains("admin createRole grantforge_bob", "admin grantRolesToUser bob");
        assertThat(sync.groupsOf("alice")).containsExactlyInAnyOrder("analysts", "readWrite");
        assertThat(sync.groupsOf("nobody")).isEmpty();
        assertThat(requireNonNull(sync.inventory(), "inventory").deployment()).isEqualTo("rs0");
        assertThat(sync.role("bob")).isEqualTo("grantforge_bob");
        assertThat(sync.summary(outcome)).startsWith("2 users, 1 roles created");
        assertThat(sync.changed(outcome)).isTrue();
    }

    @Test
    void leavesItselfAlone() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new MongoSnapshots().allow(List.of("grantforge"), List.of("read"), "database", "sales", "collection", "*").snapshot());
        sync.run();
        assertThat(mongo.changes).doesNotContain("admin createRole grantforge_grantforge");
    }

    @Test
    void looksAfterTheDatabasesItIsGivenByName() throws IOException
    {
        Sync sync = sync("grantforge.mongodb.databases", "hr");
        snapshot.set(new MongoSnapshots().allow(List.of("bob"), List.of("read"), "database", "sales", "collection", "orders").snapshot());
        sync.run();
        assertThat(mongo.changes).doesNotContain("admin createRole grantforge_bob");
    }

    @Test
    void saysWhatItWithholds() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new MongoSnapshots().allow(List.of("bob"), List.of("drop_database"), "database", "sales").snapshot());
        sync.run();
        assertThat(mongo.changes).doesNotContain("admin createRole grantforge_bob");
    }

    @Test
    void grantsNothingForAServiceNotInUse() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new MongoSnapshots().allow(List.of("bob"), List.of("read"), "database", "sales", "collection", "*").disabled().snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run(), "outcome");
        assertThat(mongo.changes).contains("admin dropRole grantforge_alice");
        assertThat(outcome.dropped()).isEqualTo(1);
        assertThat(sync.summary(new Reconciler.Outcome(1, 0, 0, 1, 0, 2))).endsWith(", 2 failed");
    }
}
