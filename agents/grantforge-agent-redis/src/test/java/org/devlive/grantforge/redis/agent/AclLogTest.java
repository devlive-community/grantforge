// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AclLogTest
{
    @TempDir
    Path directory;

    private final List<AccessEvent> events = new ArrayList<>();
    private @Nullable Snapshot snapshot;

    static List<Object> entry(long id, String reason, String object, String user, String command, long at)
    {
        return List.of("count", 1L, "reason", reason, "context", "toplevel", "object", object, "username", user, "age-seconds", "0.1", "client-info",
                "id=1 addr=10.0.0.9:51234 laddr=127.0.0.1:6379 cmd=" + command + " user=" + user, "entry-id", id, "timestamp-created", at - 5,
                "timestamp-last-updated", at);
    }

    private AclLog log(FakeRedis redis)
    {
        return new AclLog(redis, "grantforge", directory.resolve("position"), () -> snapshot, events::add);
    }

    @Test
    void readsTheEntriesOfAnAclLogAnswer()
    {
        List<AclLog.Entry> entries = AclLog.entries(List.of(entry(2, "key", "secret:1", "alice", "get", 2000), entry(1, "channel", "news", "bob", "publish", 1000)));
        assertThat(entries).containsExactly(new AclLog.Entry(1, "channel", "news", "bob", "publish", "10.0.0.9", Instant.ofEpochMilli(1000)),
                new AclLog.Entry(2, "key", "secret:1", "alice", "get", "10.0.0.9", Instant.ofEpochMilli(2000)));
        assertThat(AclLog.entries("odd")).isEmpty();
        AclLog.Entry bare = AclLog.entries(List.of(List.of("entry-id", "x", "timestamp-created", "7", "odd"))).get(0);
        assertThat(bare.id()).isZero();
        assertThat(bare.address()).isNull();
        assertThat(bare.at()).isEqualTo(Instant.ofEpochMilli(7));
        assertThat(AclLog.entries(List.of("odd")).get(0).user()).isEmpty();
    }

    @Test
    void readsTheFlagsOfACommand()
    {
        assertThat(AclLog.flags(List.of(List.of("set", -3L, List.of("write", "denyoom"))))).containsExactlyInAnyOrder("write", "denyoom");
        assertThat(AclLog.flags(List.of())).isEmpty();
        assertThat(AclLog.flags(List.of(List.of("set")))).isEmpty();
        assertThat(AclLog.flags(null)).isEmpty();
    }

    @Test
    void reportsRefusalsUpdatedSinceTheLastRead() throws IOException
    {
        FakeRedis redis = FakeRedis.replicated().answer("ACL LOG", List.of(entry(0, "key", "old", "alice", "get", 1000)))
                .answer("COMMAND INFO set", List.of(List.of("set", -3L, List.of("write")))).answer("COMMAND INFO", List.of(List.of("get", 2L, List.of("readonly"))));
        AclLog log = log(redis);
        assertThat(log.poll()).as("read from the present at first").isZero();
        redis.answer("p:6379|ACL LOG", List.of(entry(0, "key", "old", "alice", "get", 1000), entry(1, "key", "secret:1", "alice", "get", 2000),
                entry(2, "key", "sales:1", "alice", "set", 2001), entry(3, "auth", "AUTH", "alice", "auth", 2002), entry(4, "key", "x", "grantforge", "get", 2003),
                entry(5, "command", "flushall", "alice", "flushall", 2004), entry(6, "command", "info", "alice", "info", 2005)));
        snapshot = new RedisSnapshots().deny(List.of("alice"), List.of("read"), "key", "secret:*").allow(List.of("alice"), List.of("write"), "key", "sales:*").snapshot();
        assertThat(log.poll()).isEqualTo(6);
        assertThat(events).extracting(event -> EventFields.of(event).get("resource") + " " + EventFields.of(event).get("accessType")).containsExactly(
                "secret:1 read", "sales:1 write", "p:6379 monitor");
        assertThat(EventFields.of(events.get(0))).containsEntry("outcome", "DENIED").containsEntry("enforcer", "GRANTFORGE").containsEntry("clientIp", "10.0.0.9")
                .containsEntry("request", "get on p:6379");
        assertThat(EventFields.of(events.get(1))).as("a policy allows what Redis refused").containsEntry("enforcer", "NATIVE");
        events.clear();
        assertThat(log.poll()).isZero();
        assertThat(Files.readString(directory.resolve("position"))).contains("\"p:6379\":2005");
    }

    @Test
    void reportsChannelRefusals()
    {
        FakeRedis redis = FakeRedis.replicated().answer("ACL LOG", List.of());
        AclLog log = log(redis);
        log.poll();
        redis.answer("ACL LOG", List.of(entry(0, "channel", "news", "bob", "publish", 1000), entry(1, "channel", "alerts", "bob", "subscribe", 1001)));
        log.poll();
        assertThat(events).as("on both nodes").extracting(event -> EventFields.of(event).get("accessType"))
                .containsExactlyInAnyOrder("publish", "publish", "subscribe", "subscribe");
    }

    @Test
    void aNodeWhoseLogCannotBeReadIsToldOfAndOneThatCannotBeReachedFailsTheRead()
    {
        assertThat(log(FakeRedis.replicated().fail("ACL LOG", false)).poll()).isZero();
        assertThatThrownBy(() -> log(FakeRedis.replicated().fail("ACL LOG", true)).poll()).isInstanceOf(RedisFailure.class);
        FakeRedis unknown = FakeRedis.replicated().answer("ACL LOG", List.of()).fail("COMMAND INFO", false);
        AclLog log = log(unknown);
        log.poll();
        unknown.answer("ACL LOG", List.of(entry(0, "key", "k", "alice", "odd", 5000)));
        log.poll();
        assertThat(events).extracting(event -> EventFields.of(event).get("accessType")).contains("read");
    }

    @Test
    void anUnreadablePositionReadsOnFromThePresentAndAnUnwritableOneIsNotFatal() throws IOException
    {
        Files.writeString(directory.resolve("position"), "not json");
        FakeRedis redis = FakeRedis.replicated().answer("ACL LOG", List.of(entry(0, "key", "k", "alice", "get", 5000)));
        assertThat(log(redis).poll()).isZero();
        Files.delete(directory.resolve("position"));
        Files.createDirectory(directory.resolve("position"));
        Files.writeString(directory.resolve("position").resolve("taken"), "");
        assertThat(log(redis).poll()).isZero();
    }
}
