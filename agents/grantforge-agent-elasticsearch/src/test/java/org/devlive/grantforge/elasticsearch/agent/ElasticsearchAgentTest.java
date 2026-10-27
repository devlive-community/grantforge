// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchAgentTest
{
    @TempDir
    Path directory;

    @Test
    void supportsElasticsearch819AndLater()
    {
        assertThat(ElasticsearchAgent.unsupported("", "8.19.23")).isNull();
        assertThat(ElasticsearchAgent.unsupported("", "9.5.5")).isNull();
        assertThat(ElasticsearchAgent.unsupported("", "8.18.8")).contains("Elasticsearch 8.18.8", "needs Elasticsearch 8.19 or later");
        assertThat(ElasticsearchAgent.unsupported("", "7.17.28")).contains("needs Elasticsearch 8.19");
        assertThat(ElasticsearchAgent.unsupported("", "next")).contains("cannot read");
        assertThat(ElasticsearchAgent.unsupported("opensearch", "3.9.0")).contains("OpenSearch agent");
    }

    @Test
    void refusesAClusterItCannotKeepAndLetsGoOfIt() throws Exception
    {
        Map<String, String> values = AgentConfigTest.minimal(directory);
        FakeElasticsearch old = new FakeElasticsearch().answer("GET /", FakeElasticsearch.json("{\"version\": {\"number\": \"8.17.2\"}}"));
        assertThatThrownBy(() -> ElasticsearchAgent.start(AgentConfig.parse(values, "1"), connection -> old)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8.17.2");
        assertThat(old.closed()).isTrue();
        FakeElasticsearch bare = new FakeElasticsearch().answer("GET /_xpack", FakeElasticsearch.json("{\"features\": {\"security\": {\"enabled\": false}}}"));
        assertThatThrownBy(() -> ElasticsearchAgent.start(AgentConfig.parse(values, "1"), connection -> bare)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without security");
        FakeElasticsearch refusing = new FakeElasticsearch().fail("GET /_security/_authenticate", 401);
        assertThatThrownBy(() -> ElasticsearchAgent.start(AgentConfig.parse(values, "1"), connection -> refusing)).isInstanceOf(ElasticsearchFailure.class);
        assertThat(refusing.closed()).isTrue();
    }

    @Test
    void runsTheSyncAndTheAuditLogUntilClosed() throws Exception
    {
        try (AgentServer server = new AgentServer(new ElasticsearchSnapshots())) {
            Map<String, String> values = AgentConfigTest.minimal(directory);
            values.put("grantforge.server.url", "http://127.0.0.1:" + server.port());
            values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), AgentServer.TOKEN).toString());
            values.put("grantforge.sync.interval.ms", "3600000");
            values.put("grantforge.audit.log.interval.ms", "600000");
            values.put("grantforge.audit.log.files", Files.writeString(directory.resolve("audit.json"), "").toString());
            List<FakeElasticsearch> connections = new ArrayList<>();
            ElasticsearchAgent agent = ElasticsearchAgent.start(AgentConfig.parse(values, "1"), connection -> {
                FakeElasticsearch search = ServerStateTest.cluster();
                connections.add(search);
                return search;
            });
            try {
                assertThat(connections).as("the audit log is read from files, not through the cluster").hasSize(1);
                assertThat(agent.readLogNow()).isZero();
                assertThat(agent.agent()).isNotNull();
                assertThat(agent.role("alice")).isEqualTo("grantforge_alice");
                agent.syncNow();
            }
            finally {
                agent.close();
            }
            assertThat(connections).allSatisfy(connection -> assertThat(connection.closed()).isTrue());

            values.remove("grantforge.audit.log.files");
            FakeElasticsearch only = ServerStateTest.cluster().fail("GET " + Reconciler.ROLES, 503);
            try (ElasticsearchAgent quiet = ElasticsearchAgent.start(AgentConfig.parse(values, "1"), connection -> only)) {
                assertThat(quiet.readLogNow()).isZero();
            }
        }
    }
}
