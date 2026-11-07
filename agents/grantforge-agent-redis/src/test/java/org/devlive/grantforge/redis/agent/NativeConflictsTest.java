// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeConflictsTest
{
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final Logger logger = Logger.getLogger(NativeConflicts.class.getName());
    private final Handler capture = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            warnings.add(record.getMessage());
        }

        @Override
        public void flush()
        {
            // Nothing is buffered.
        }

        @Override
        public void close()
        {
            // Nothing to let go of.
        }
    };

    @BeforeEach
    void listen()
    {
        logger.addHandler(capture);
    }

    @AfterEach
    void stopListening()
    {
        logger.removeHandler(capture);
    }

    private static Snapshot denying(String type, String level, String value)
    {
        return new RedisSnapshots().deny(List.of("alice"), List.of(type), level, value).snapshot();
    }

    private void check(FakeRedis redis, Snapshot snapshot)
    {
        new NativeConflicts(redis).check(ServerState.read(redis), Set.of("alice"), snapshot, Instant.now());
    }

    @Test
    void asksRedisWhetherAUserMayDoWhatAPolicyDenies()
    {
        FakeRedis redis = ServerStateTest.deployment().answer("ACL DRYRUN alice GET hr:x", "OK").answer("ACL DRYRUN", "User alice has no permissions");
        check(redis, denying("read", "key", "hr:*"));
        assertThat(warnings).contains("Redis user alice may read key hr:* through permissions outside GrantForge, which GrantForge policy 1 denies");
        warnings.clear();
        check(redis, denying("write", "key", "hr:*"));
        assertThat(warnings).noneMatch(warning -> warning.contains("alice may"));
    }

    @Test
    void asksOfChannelsAndOfEachNode()
    {
        FakeRedis redis = ServerStateTest.deployment().answer("ACL DRYRUN alice PUBLISH news:x x", "OK").answer("r:6379|ACL DRYRUN alice INFO", "OK")
                .answer("ACL DRYRUN", "no");
        check(redis, denying("publish", "channel", "news:*"));
        assertThat(warnings).anyMatch(warning -> warning.contains("may publish channel news:*"));
        check(redis, denying("monitor", "node", "r:6379"));
        assertThat(warnings).anyMatch(warning -> warning.contains("may monitor node r:6379"));
    }

    @Test
    void warnsOfUsersNoSelectorBindsAndUsersAnyoneMaySignInAsOnce()
    {
        FakeRedis redis = ServerStateTest.deployment().answer("ACL DRYRUN", "no");
        NativeConflicts conflicts = new NativeConflicts(redis);
        conflicts.check(ServerState.read(redis), Set.of("alice"), new RedisSnapshots().snapshot(), Instant.now());
        conflicts.check(ServerState.read(redis), Set.of("alice"), new RedisSnapshots().snapshot(), Instant.now());
        assertThat(warnings).containsExactlyInAnyOrder("Redis user boss may run every command on every key, which no selector binds; GrantForge keeps no selector for it",
                "Redis user open needs no password on p:6379, so anyone may sign in as it", "Redis user open needs no password on r:6379, so anyone may sign in as it");
    }

    @Test
    void aDryRunRedisRefusesIsPassedOverAndOneThatCannotReachFails()
    {
        check(ServerStateTest.deployment().fail("ACL DRYRUN", false), denying("read", "key", "hr:*"));
        assertThat(warnings).noneMatch(warning -> warning.contains("alice may"));
        assertThatThrownBy(() -> check(ServerStateTest.deployment().fail("ACL DRYRUN", true), denying("read", "key", "hr:*"))).isInstanceOf(RedisFailure.class);
    }

    @Test
    void namesAKeyAPatternMatches()
    {
        assertThat(NativeConflicts.example("hr:*:?")).isEqualTo("hr:x:x");
        FakeRedis empty = ServerStateTest.deployment().topology(new Topology("Redis", "7.2.17", false, List.of()));
        check(empty, denying("read", "key", "hr:*"));
        assertThat(warnings).isEmpty();
    }
}
