// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcilerTest
{
    private static final Selector READS = Selector.of(List.of("+@read"), new TreeSet<>(List.of("%R~sales:*")), new TreeSet<>());
    private static final Plan PLAN = new Plan(List.of(READS), Set.of());

    @Test
    void replacesEveryNodesSelectorsInOneCommandAndSavesTheAclFile()
    {
        FakeRedis redis = ServerStateTest.deployment();
        Reconciler reconciler = new Reconciler(redis);
        Reconciler.Outcome outcome = reconciler.reconcile(ServerState.read(redis), Set.of("alice"), user -> PLAN);
        assertThat(redis.changes).containsExactly("p:6379 ACL SETUSER alice clearselectors (%R~sales:* -@all +@read)", "p:6379 ACL SAVE",
                "r:6379 ACL SETUSER alice clearselectors (%R~sales:* -@all +@read)");
        assertThat(outcome).isEqualTo(new Reconciler.Outcome(1, 2, 1, 0, 0));
        assertThat(outcome.changed()).isTrue();
    }

    @Test
    void leavesWhatIsInStepAloneAsWrittenOrAsRedisShowedIt()
    {
        FakeRedis redis = ServerStateTest.deployment().answer("ACL GETUSER alice", UserAclTest.getuser("-@all", "", List.of(UserAclTest.selector("-@all +@read",
                "%R~sales:*", "")), "on"));
        Reconciler reconciler = new Reconciler(redis);
        Reconciler.Outcome outcome = reconciler.reconcile(ServerState.read(redis), Set.of("alice"), user -> PLAN);
        assertThat(redis.changes).isEmpty();
        assertThat(outcome.changed()).isFalse();

        FakeRedis rewritten = ServerStateTest.deployment().answer("ACL GETUSER alice", UserAclTest.getuser("-@all", "", List.of(UserAclTest.selector("-@all +@read +get",
                "%R~sales:*", "")), "on"));
        Reconciler remembering = new Reconciler(rewritten);
        remembering.reconcile(ServerState.read(rewritten), Set.of("alice"), user -> PLAN);
        rewritten.changes.clear();
        assertThat(remembering.reconcile(ServerState.read(rewritten), Set.of("alice"), user -> PLAN).changed()).as("Redis wrote the rule its own way").isFalse();
        assertThat(remembering.reconcile(ServerState.read(rewritten), Set.of("alice"), user -> Plan.NOTHING).replaced()).isEqualTo(2);
    }

    @Test
    void clearsTheSelectorsOfAUserGivenNothing()
    {
        FakeRedis redis = ServerStateTest.deployment();
        new Reconciler(redis).reconcile(ServerState.read(redis), Set.of("alice"), user -> Plan.NOTHING);
        assertThat(redis.changes).contains("p:6379 ACL SETUSER alice clearselectors", "r:6379 ACL SETUSER alice clearselectors");
    }

    @Test
    void countsUsersANodeLacks()
    {
        FakeRedis redis = ServerStateTest.deployment();
        assertThat(new Reconciler(redis).reconcile(ServerState.read(redis), Set.of("ghost"), user -> PLAN).missing()).isEqualTo(2);
    }

    @Test
    void aChangeThatFailsDoesNotHoldBackTheOthers()
    {
        FakeRedis redis = ServerStateTest.deployment().fail("p:6379|ACL SETUSER", false);
        Reconciler.Outcome outcome = new Reconciler(redis).reconcile(ServerState.read(redis), Set.of("alice"), user -> PLAN);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(redis.changes).containsExactly("r:6379 ACL SETUSER alice clearselectors (%R~sales:* -@all +@read)");
        FakeRedis unsaved = ServerStateTest.deployment().fail("p:6379|ACL SAVE", false);
        assertThat(new Reconciler(unsaved).reconcile(ServerState.read(unsaved), Set.of("alice"), user -> PLAN).failed()).isEqualTo(1);
    }

    @Test
    void aNodeThatCannotBeReachedFailsTheSync()
    {
        FakeRedis down = ServerStateTest.deployment().fail("p:6379|ACL SETUSER", true);
        assertThatThrownBy(() -> new Reconciler(down).reconcile(ServerState.read(down), Set.of("alice"), user -> PLAN)).isInstanceOf(RedisFailure.class);
        FakeRedis unsaved = ServerStateTest.deployment().fail("p:6379|ACL SAVE", true);
        assertThatThrownBy(() -> new Reconciler(unsaved).reconcile(ServerState.read(unsaved), Set.of("alice"), user -> PLAN)).isInstanceOf(RedisFailure.class);
    }
}
