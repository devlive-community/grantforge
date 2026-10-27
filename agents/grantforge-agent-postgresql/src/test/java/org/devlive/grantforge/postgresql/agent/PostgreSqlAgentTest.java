// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgreSqlAgentTest
{
    private static final String SERVER = "server_version_num')::int AS number";

    @TempDir
    Path directory;

    private static Row server(int number, boolean superuser)
    {
        return Row.of("version", number / 10_000 + "." + number % 100, "number", number, "superuser", superuser, "self", "grantforge");
    }

    @Test
    void supportsPostgreSql15AndLater()
    {
        assertThat(PostgreSqlAgent.unsupported(150_000, "15.0")).isNull();
        assertThat(PostgreSqlAgent.unsupported(180_006, "18.6")).isNull();
        assertThat(PostgreSqlAgent.unsupported(140_019, "14.19")).contains("PostgreSQL 14.19", "needs PostgreSQL 15 or later");
    }

    @Test
    void refusesAServerItDoesNotSupportAndLetsGoOfIt() throws Exception
    {
        FakePostgreSql old = new FakePostgreSql().answer(SERVER, server(140_019, true));
        Map<String, String> values = AgentConfigTest.minimal(directory);
        assertThatThrownBy(() -> PostgreSqlAgent.start(AgentConfig.parse(values, "1"), connection -> old)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("14.19");
        assertThat(old.closed).isTrue();
    }

    @Test
    void runsTheSyncAndTheServerLogUntilClosed() throws Exception
    {
        try (AgentServer server = new AgentServer(new PostgreSqlSnapshots())) {
            Map<String, String> values = AgentConfigTest.minimal(directory);
            values.put("grantforge.server.url", "http://127.0.0.1:" + server.port());
            values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), AgentServer.TOKEN).toString());
            values.put("grantforge.sync.interval.ms", "3600000");
            values.put("grantforge.audit.server.log.interval.ms", "600000");
            List<FakePostgreSql> connections = new ArrayList<>();
            PostgreSqlAgent agent = PostgreSqlAgent.start(AgentConfig.parse(values, "1"), connection -> {
                FakePostgreSql postgres = connections.isEmpty() ? ServerStateTest.server().answer(SERVER, server(170_011, false))
                        : new FakePostgreSql().answer("current_setting('log_destination')", Row.of("destination", "stderr"));
                connections.add(postgres);
                return postgres;
            });
            try {
                assertThat(connections).as("one to sync, one to read the log").hasSize(2);
                assertThat(agent.readLogNow()).isZero();
                assertThat(agent.agent()).isNotNull();
                assertThat(agent.role("alice")).isEqualTo("grantforge_alice");
                agent.syncNow();
            }
            finally {
                agent.close();
            }
            assertThat(connections).allSatisfy(connection -> assertThat(connection.closed).isTrue());

            values.put("grantforge.audit.server.log", "false");
            FakePostgreSql only = ServerStateTest.server().answer(SERVER, server(170_011, true)).fail("FROM pg_roles r ORDER BY", "08006");
            try (PostgreSqlAgent quiet = PostgreSqlAgent.start(AgentConfig.parse(values, "1"), connection -> only)) {
                assertThat(quiet.readLogNow()).isZero();
            }
        }
    }
}
