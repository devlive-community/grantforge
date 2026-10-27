// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchAgentTest
{
    @TempDir
    Path directory;

    @Test
    void supportsOpenSearch219AndLater()
    {
        assertThat(OpenSearchAgent.unsupported("opensearch", "2.19.7")).isNull();
        assertThat(OpenSearchAgent.unsupported("opensearch", "3.0.0")).isNull();
        assertThat(OpenSearchAgent.unsupported("opensearch", "2.18.0")).contains("OpenSearch 2.18.0", "needs OpenSearch 2.19 or later");
        assertThat(OpenSearchAgent.unsupported("opensearch", "1.3.20")).contains("needs OpenSearch 2.19");
        assertThat(OpenSearchAgent.unsupported("opensearch", "next")).contains("cannot read");
        assertThat(OpenSearchAgent.unsupported("", "8.15.0")).contains("no OpenSearch cluster");
    }

    @Test
    void refusesAClusterItCannotKeepAndLetsGoOfIt() throws Exception
    {
        Map<String, String> values = AgentConfigTest.minimal(directory);
        FakeOpenSearch old = new FakeOpenSearch().answer("GET /", FakeOpenSearch.json("{\"version\": {\"distribution\": \"opensearch\","
                + " \"number\": \"2.11.0\"}}"));
        assertThatThrownBy(() -> OpenSearchAgent.start(AgentConfig.parse(values, "1"), connection -> old)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2.11.0");
        assertThat(old.closed()).isTrue();
        FakeOpenSearch bare = new FakeOpenSearch().fail("GET /_plugins/_security/authinfo", 400);
        assertThatThrownBy(() -> OpenSearchAgent.start(AgentConfig.parse(values, "1"), connection -> bare)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without the security plugin");
        FakeOpenSearch refusing = new FakeOpenSearch().fail("GET /_plugins/_security/authinfo", 401);
        assertThatThrownBy(() -> OpenSearchAgent.start(AgentConfig.parse(values, "1"), connection -> refusing)).isInstanceOf(OpenSearchFailure.class);
        assertThat(refusing.closed()).isTrue();
    }

    @Test
    void runsTheSyncAndTheAuditLogUntilClosed() throws Exception
    {
        try (AgentServer server = new AgentServer(new OpenSearchSnapshots())) {
            Map<String, String> values = AgentConfigTest.minimal(directory);
            values.put("grantforge.server.url", "http://127.0.0.1:" + server.port());
            values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), AgentServer.TOKEN).toString());
            values.put("grantforge.sync.interval.ms", "3600000");
            values.put("grantforge.audit.log.interval.ms", "600000");
            List<FakeOpenSearch> connections = new ArrayList<>();
            OpenSearchAgent agent = OpenSearchAgent.start(AgentConfig.parse(values, "1"), connection -> {
                FakeOpenSearch search = ServerStateTest.cluster().answer("POST /security-auditlog-*/_search", FakeOpenSearch.json("{\"hits\": {}}"));
                connections.add(search);
                return search;
            });
            try {
                assertThat(connections).as("one to sync, one to read the audit log").hasSize(2);
                assertThat(agent.readLogNow()).isZero();
                assertThat(agent.agent()).isNotNull();
                assertThat(agent.role("alice")).isEqualTo("grantforge_alice");
                agent.syncNow();
            }
            finally {
                agent.close();
            }
            assertThat(connections).allSatisfy(connection -> assertThat(connection.closed()).isTrue());

            values.put("grantforge.audit.log", "false");
            FakeOpenSearch only = ServerStateTest.cluster().fail("GET " + Reconciler.ROLES, 503);
            try (OpenSearchAgent quiet = OpenSearchAgent.start(AgentConfig.parse(values, "1"), connection -> only)) {
                assertThat(quiet.readLogNow()).isZero();
            }
        }
    }
}
