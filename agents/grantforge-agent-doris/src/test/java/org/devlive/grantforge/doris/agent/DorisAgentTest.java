// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DorisAgentTest
{
    @TempDir
    Path directory;

    @Test
    void supportsDoris2Point1AndLater()
    {
        assertThat(DorisAgent.unsupported("Doris version doris-2.1.11-rc01-97b77e6cda")).isNull();
        assertThat(DorisAgent.unsupported("doris version doris-4.1.5-release-917b20be838")).isNull();
        assertThat(DorisAgent.unsupported("doris-2.0.15-rc01")).contains("Apache Doris 2.0.15; the GrantForge agent needs Apache Doris 2.1 or later");
        assertThat(DorisAgent.unsupported("doris-1.2.8")).contains("needs Apache Doris 2.1");
        assertThat(DorisAgent.unsupported("5.7.99")).contains("cannot be read");
        assertThat(DorisAgent.release("Doris version doris-2.1.11-rc01")).isEqualTo("2.1.11");
        assertThat(DorisAgent.release("custom")).isEqualTo("custom");
    }

    @Test
    void refusesAClusterItDoesNotSupportAndLetsGoOfIt() throws Exception
    {
        FakeDoris old = new FakeDoris().answer("version_comment", Row.of("Variable_name", "version_comment", "Value", "doris-2.0.15-rc01"));
        Map<String, String> values = AgentConfigTest.minimal(directory);
        assertThatThrownBy(() -> DorisAgent.start(AgentConfig.parse(values, "1"), connection -> old)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2.0.15");
        assertThat(old.closed).isTrue();
        FakeDoris silent = new FakeDoris();
        assertThatThrownBy(() -> DorisAgent.start(AgentConfig.parse(values, "1"), connection -> silent)).hasMessageContaining("cannot be read");
    }

    @Test
    void runsTheSyncAndTheAuditLogUntilClosed() throws Exception
    {
        try (AgentServer server = new AgentServer(new DorisSnapshots())) {
            Map<String, String> values = AgentConfigTest.minimal(directory);
            values.put("grantforge.server.url", "http://127.0.0.1:" + server.port());
            values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), AgentServer.TOKEN).toString());
            values.put("grantforge.sync.interval.ms", "3600000");
            values.put("grantforge.audit.log.interval.ms", "600000");
            List<FakeDoris> connections = new ArrayList<>();
            DorisAgent agent = DorisAgent.start(AgentConfig.parse(values, "1"), connection -> {
                FakeDoris doris = connections.isEmpty() ? ServerStateTest.cluster().answer("version_comment", Row.of("Variable_name",
                        "version_comment", "Value", "doris version doris-4.1.5-release-917b20be838")) : AuditLogTest.cluster();
                connections.add(doris);
                return doris;
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

            values.put("grantforge.audit.log", "false");
            FakeDoris only = ServerStateTest.cluster().answer("version_comment", Row.of("Variable_name", "version_comment", "Value",
                    "doris-2.1.11-rc01")).fail("SHOW ROLES", true);
            try (DorisAgent quiet = DorisAgent.start(AgentConfig.parse(values, "1"), connection -> only)) {
                assertThat(quiet.readLogNow()).isZero();
            }
        }
    }
}
