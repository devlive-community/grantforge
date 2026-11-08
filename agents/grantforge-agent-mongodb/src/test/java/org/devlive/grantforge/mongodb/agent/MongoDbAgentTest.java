// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoDbAgentTest
{
    @TempDir
    Path directory;

    private static FakeMongo deployment(String version)
    {
        return ServerStateTest.deployment().answer("buildInfo", new Document("version", version)).answer("connectionStatus",
                new Document("authInfo", new Document("authenticatedUsers", List.of(new Document("user", "CN=agent").append("db", "$external")))));
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
    void supportsMongoDb6AndLater()
    {
        assertThat(MongoDbAgent.unsupported("6.0.27")).isNull();
        assertThat(MongoDbAgent.unsupported("9.0.4")).isNull();
        assertThat(MongoDbAgent.unsupported("8.0.29-13")).isNull();
        assertThat(MongoDbAgent.unsupported("5.0.31")).contains("MongoDB 5.0.31; the GrantForge agent needs MongoDB 6.0 or later");
        assertThat(MongoDbAgent.unsupported("old")).contains("cannot be read");
    }

    @Test
    void refusesADeploymentItDoesNotSupportAndLetsGoOfIt() throws Exception
    {
        FakeMongo old = deployment("5.0.31");
        Map<String, String> values = AgentConfigTest.minimal(directory);
        assertThatThrownBy(() -> MongoDbAgent.start(AgentConfig.parse(values, "1"), connection -> old)).isInstanceOf(IllegalStateException.class).hasMessageContaining("5.0.31");
        assertThat(old.closed).isTrue();
    }

    @Test
    void runsTheSyncAndTheProfilerUntilClosed() throws Exception
    {
        try (AgentServer server = new AgentServer(new MongoSnapshots())) {
            FakeMongo mongo = deployment("8.0.34");
            MongoDbAgent agent = MongoDbAgent.start(AgentConfig.parse(settings(server), "1"), connection -> mongo);
            try {
                assertThat(agent.readAccessesNow()).isZero();
                assertThat(agent.agent()).isNotNull();
                agent.syncNow();
            }
            finally {
                agent.close();
            }
            assertThat(mongo.closed).isTrue();
        }
    }

    @Test
    void readsTheAuditFilesWhenItIsGivenThem() throws Exception
    {
        try (AgentServer server = new AgentServer(new MongoSnapshots())) {
            Path file = Files.writeString(directory.resolve("audit.json"), "");
            Map<String, String> values = settings(server);
            values.put("grantforge.mongodb.audit.files", file.toString());
            try (MongoDbAgent agent = MongoDbAgent.start(AgentConfig.parse(values, "1"), connection -> deployment("8.0.34"))) {
                assertThat(agent.readAccessesNow()).isZero();
            }
        }
    }

    @Test
    void canLeaveAccessesUnread() throws Exception
    {
        try (AgentServer server = new AgentServer(new MongoSnapshots())) {
            Map<String, String> values = settings(server);
            values.put("grantforge.audit.log", "false");
            FakeMongo anonymous = deployment("8.0.34").answer("connectionStatus", new Document());
            try (MongoDbAgent agent = MongoDbAgent.start(AgentConfig.parse(values, "1"), connection -> anonymous)) {
                assertThat(agent.readAccessesNow()).isZero();
            }
        }
    }
}
