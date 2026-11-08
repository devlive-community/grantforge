// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

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

    private final FakeRedis redis = ServerStateTest.deployment().answer("ACL DRYRUN", "no");
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();

    private Sync sync(String... more) throws IOException
    {
        var values = AgentConfigTest.minimal(directory);
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new Sync(redis, AgentConfig.parse(values, "1"), "grantforge", snapshot::get, Clock.systemUTC());
    }

    @Test
    void leavesRedisAloneWithoutPolicies() throws IOException
    {
        assertThat(sync().run()).isNull();
        assertThat(redis.changes).isEmpty();
    }

    @Test
    void appliesThePoliciesToTheUsersItLooksAfter() throws IOException
    {
        Sync sync = sync("grantforge.redis.unmanaged.users", "open");
        snapshot.set(new RedisSnapshots().allow(List.of("alice", "boss", "open", "grantforge"), List.of("read"), "key", "sales:*").snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run(), "outcome");
        assertThat(redis.changes).containsExactly("p:6379 ACL SETUSER alice clearselectors (%R~sales:* -@all +@read -@dangerous -scan -randomkey)", "p:6379 ACL SAVE",
                "r:6379 ACL SETUSER alice clearselectors (%R~sales:* -@all +@read -@dangerous -scan -randomkey)");
        assertThat(sync.summary(outcome)).isEqualTo("1 users, 2 users' selectors replaced, 1 ACL files saved");
        assertThat(sync.summary(new Reconciler.Outcome(1, 0, 0, 0, 2))).endsWith(", 2 failed");
        assertThat(sync.changed(outcome)).isTrue();
    }

    @Test
    void grantsNothingForAServiceNotInUse() throws IOException
    {
        Sync sync = sync();
        snapshot.set(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "sales:*").disabled().snapshot());
        sync.run();
        assertThat(redis.changes).as("open holds no selector to clear").containsExactly("p:6379 ACL SETUSER alice clearselectors", "p:6379 ACL SAVE",
                "r:6379 ACL SETUSER alice clearselectors");
    }
}
