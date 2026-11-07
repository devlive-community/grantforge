// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerStateTest
{
    /** A primary with one replica, its users and their ACLs, the primary keeping its ACL in a file. */
    static FakeRedis deployment()
    {
        return FakeRedis.replicated().answer("ACL USERS", List.of("alice", "boss", "open", "grantforge"))
                .answer("ACL GETUSER alice", UserAclTest.getuser("-@all +@connection", "", List.of(UserAclTest.selector("-@all +@read", "%R~old:*", "")), "on"))
                .answer("ACL GETUSER boss", UserAclTest.getuser("+@all", "~*", List.of(), "on"))
                .answer("ACL GETUSER open", UserAclTest.getuser("-@all", "", List.of(), "on", "nopass"))
                .answer("ACL GETUSER grantforge", UserAclTest.getuser("-@all +acl", "", List.of(), "on"))
                .answer("p:6379|CONFIG GET aclfile", List.of("aclfile", "/data/users.acl"))
                .answer("CONFIG GET aclfile", List.of("aclfile", ""));
    }

    @Test
    void readsEveryNodesUsers()
    {
        ServerState state = ServerState.read(deployment());
        assertThat(state.names()).containsExactly("alice", "boss", "grantforge", "open");
        assertThat(requireNonNull(state.acl("r:6379", "alice"), "acl").selectors()).extracting(Selector::rule).containsExactly("(%R~old:* -@all +@read)");
        assertThat(state.acl("x:1", "alice")).isNull();
        assertThat(state.acl("p:6379", "ghost")).isNull();
        assertThat(state.superuser("boss")).isTrue();
        assertThat(state.superuser("alice")).isFalse();
        assertThat(state.aclFiles()).containsExactly("p:6379");
    }

    @Test
    void aNodeThatWillNotSayWhereItsAclIsKeepsNoFile()
    {
        assertThat(ServerState.read(deployment().fail("CONFIG GET", false)).aclFiles()).isEmpty();
        assertThatThrownBy(() -> ServerState.read(deployment().fail("CONFIG GET", true))).isInstanceOf(RedisFailure.class);
        assertThat(ServerState.read(deployment().answer("ACL USERS", "odd")).names()).isEmpty();
        assertThat(ServerState.read(deployment().answer("p:6379|CONFIG GET aclfile", List.of("aclfile"))).aclFiles()).isEmpty();
    }
}
