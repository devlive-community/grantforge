// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisAgentTest
{
    @TempDir
    Path directory;

    private static FakeRedis deployment(String version)
    {
        return ServerStateTest.deployment().answer("INFO server", "redis_version:" + version).answer("ACL WHOAMI", "grantforge").answer("ACL LOG", List.of())
                .answer("ACL DRYRUN", "no");
    }

    private Map<String, String> settings(AgentServer server) throws Exception
    {
        Map<String, String> values = AgentConfigTest.minimal(directory);
        values.put("grantforge.server.url", "http://127.0.0.1:" + server.port());
        values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), AgentServer.TOKEN).toString());
        values.put("grantforge.sync.interval.ms", "3600000");
        values.put("grantforge.audit.log.interval.ms", "600000");
        return values;
    }

    @Test
    void supportsRedis72AndLaterAndValkey()
    {
        assertThat(RedisAgent.unsupported("7.2.17")).isNull();
        assertThat(RedisAgent.unsupported("8.10.3")).isNull();
        assertThat(RedisAgent.unsupported("7.0.15")).contains("Redis 7.0.15; the GrantForge agent needs Redis 7.2 or Valkey");
        assertThat(RedisAgent.unsupported("6.2.24")).contains("needs Redis 7.2");
        assertThat(RedisAgent.unsupported("odd")).contains("cannot be read");
    }

    @Test
    void refusesADeploymentItDoesNotSupportAndLetsGoOfIt() throws Exception
    {
        FakeRedis old = deployment("7.0.15");
        Map<String, String> values = AgentConfigTest.minimal(directory);
        assertThatThrownBy(() -> RedisAgent.start(AgentConfig.parse(values, "1"), connection -> old)).isInstanceOf(IllegalStateException.class).hasMessageContaining("7.0.15");
        assertThat(old.closed).isTrue();
    }

    @Test
    void runsTheSyncAndTheAclLogsUntilClosed() throws Exception
    {
        try (AgentServer server = new AgentServer(new RedisSnapshots())) {
            FakeRedis syncing = deployment("8.10.3");
            FakeRedis reading = deployment("8.10.3");
            List<FakeRedis> connections = new ArrayList<>(List.of(syncing, reading));
            RedisAgent agent = RedisAgent.start(AgentConfig.parse(settings(server), "1"), connection -> connections.remove(0));
            try {
                assertThat(agent.readLogNow()).isZero();
                assertThat(agent.agent()).isNotNull();
                agent.syncNow();
            }
            finally {
                agent.close();
            }
            assertThat(syncing.closed).isTrue();
            assertThat(reading.closed).isTrue();
        }
    }

    @Test
    void canLeaveTheAclLogsUnread() throws Exception
    {
        try (AgentServer server = new AgentServer(new RedisSnapshots())) {
            Map<String, String> values = settings(server);
            values.put("grantforge.audit.log", "false");
            try (RedisAgent agent = RedisAgent.start(AgentConfig.parse(values, "1"), connection -> deployment("8.10.3"))) {
                assertThat(agent.readLogNow()).isZero();
            }
        }
    }
}
