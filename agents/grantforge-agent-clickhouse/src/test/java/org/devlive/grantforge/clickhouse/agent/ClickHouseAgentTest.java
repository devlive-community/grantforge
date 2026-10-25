// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseAgentTest
{
    @TempDir
    Path directory;

    @Test
    void supportsClickHouse24Point8AndLater()
    {
        assertThat(ClickHouseAgent.unsupported("24.8.14.39")).isNull();
        assertThat(ClickHouseAgent.unsupported("25.1.1")).isNull();
        assertThat(ClickHouseAgent.unsupported("24.3.1")).contains("needs ClickHouse 24.8 or later");
        assertThat(ClickHouseAgent.unsupported("23.12.1")).contains("needs ClickHouse 24.8");
        assertThat(ClickHouseAgent.unsupported("nightly")).contains("cannot be read");
    }

    @Test
    void refusesAServerItDoesNotSupportAndLetsGoOfIt() throws Exception
    {
        FakeClickHouse old = new FakeClickHouse().answer("version()", Row.of("version", "23.8.1.1"));
        Map<String, String> values = AgentConfigTest.minimal(directory);
        assertThatThrownBy(() -> ClickHouseAgent.start(AgentConfig.parse(values, "1"), connection -> old)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("23.8.1.1");
        assertThat(old.closed).isTrue();
    }

    @Test
    void runsTheSyncAndTheQueryLogUntilClosed() throws Exception
    {
        try (AgentServer server = new AgentServer(new ClickHouseSnapshots())) {
            Map<String, String> values = AgentConfigTest.minimal(directory);
            values.put("grantforge.server.url", "http://127.0.0.1:" + server.port());
            values.put("grantforge.token.file", java.nio.file.Files.writeString(directory.resolve("token"), AgentServer.TOKEN).toString());
            values.put("grantforge.sync.interval.ms", "3600000");
            values.put("grantforge.audit.query.log.interval.ms", "600000");
            List<FakeClickHouse> connections = new ArrayList<>();
            ClickHouseAgent agent = ClickHouseAgent.start(AgentConfig.parse(values, "1"), connection -> {
                FakeClickHouse clickhouse = connections.isEmpty() ? ServerStateTest.server().answer("version()", Row.of("version", "26.8.34.13"))
                        : new FakeClickHouse().answer("now64(6)) AS micros", Row.of("micros", 1L));
                connections.add(clickhouse);
                return clickhouse;
            });
            try {
                assertThat(connections).as("one to sync, one to read the log").hasSize(2);
                assertThat(agent.readLogNow()).isZero();
                assertThat(agent.agent()).isNotNull();
                agent.syncNow();
            }
            finally {
                agent.close();
            }
            assertThat(connections).allSatisfy(connection -> assertThat(connection.closed).isTrue());

            values.put("grantforge.audit.query.log", "false");
            FakeClickHouse only = ServerStateTest.server().answer("version()", Row.of("version", "26.8.34.13"))
                    .fail("system.role_grants", ClickHouseFailure.UNREACHABLE);
            try (ClickHouseAgent quiet = ClickHouseAgent.start(AgentConfig.parse(values, "1"), connection -> only)) {
                assertThat(quiet.readLogNow()).isZero();
            }
        }
    }
}
