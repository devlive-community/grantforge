// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgentConfigTest
{
    @TempDir
    Path directory;

    /** The settings an agent needs and no more, its token written into a directory. */
    static Map<String, String> minimal(Path directory) throws IOException
    {
        Path token = Files.writeString(directory.resolve("token"), "gfa_token", StandardCharsets.UTF_8);
        return new HashMap<>(Map.of("grantforge.server.url", "https://grantforge.example.com", "grantforge.token.file", token.toString(),
                "grantforge.instance", "redis-1", "grantforge.cache.dir", directory.resolve("cache").toString(), "grantforge.redis.nodes", "redis1,Redis2:6380"));
    }

    private Map<String, String> settings(String... more) throws IOException
    {
        Map<String, String> values = minimal(directory);
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return values;
    }

    @Test
    void readsTheDefaults() throws IOException
    {
        AgentConfig config = AgentConfig.parse(settings(), "test");
        AgentConfig.Connection connection = config.connection();
        assertThat(connection.nodes()).containsExactly("redis1:6379", "redis2:6380");
        assertThat(connection.discover()).isTrue();
        assertThat(connection.user()).isNull();
        assertThat(connection.password()).isNull();
        assertThat(connection.tlsMode()).isEqualTo(AgentConfig.DISABLE);
        assertThat(connection.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.unmanaged()).isEmpty();
        assertThat(config.aclLog()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void readsEverySetting() throws IOException
    {
        Path password = Files.writeString(directory.resolve("password"), " agent-secret\n");
        Path keyPassword = Files.writeString(directory.resolve("key-password"), "key-secret\n");
        AgentConfig config = AgentConfig.parse(settings("grantforge.redis.user", "grantforge", "grantforge.redis.password.file", password.toString(),
                "grantforge.redis.discover", "false", "grantforge.redis.tls.mode", "VERIFY-FULL", "grantforge.redis.tls.ca.file", "/ca.pem",
                "grantforge.redis.tls.certificate.file", "/client.pem", "grantforge.redis.tls.key.file", "/client-key.pem", "grantforge.redis.tls.key.password.file",
                keyPassword.toString(), "grantforge.redis.timeout.ms", "7000", "grantforge.redis.unmanaged.users", "etl, svc", "grantforge.audit.log.interval.ms", "1000"),
                "test");
        AgentConfig.Connection connection = config.connection();
        assertThat(connection.user()).isEqualTo("grantforge");
        assertThat(connection.password()).isEqualTo("agent-secret");
        assertThat(connection.discover()).isFalse();
        assertThat(connection.tlsMode()).isEqualTo(AgentConfig.VERIFY_FULL);
        assertThat(connection.caFile()).isEqualTo("/ca.pem");
        assertThat(connection.certificateFile()).isEqualTo("/client.pem");
        assertThat(connection.keyFile()).isEqualTo("/client-key.pem");
        assertThat(connection.keyPassword()).isEqualTo("key-secret");
        assertThat(connection.timeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(connection.toString()).contains("grantforge").doesNotContain("agent-secret");
        assertThat(config.unmanaged()).containsExactlyInAnyOrder("etl", "svc");
        assertThat(config.aclLog()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void theAclLogsCanBeLeftUnread() throws IOException
    {
        assertThat(AgentConfig.parse(settings("grantforge.audit.log", "false"), "test").aclLog()).isNull();
    }

    @Test
    void refusesWhatIsNoNode() throws IOException
    {
        for (String wrong : new String[] {"redis 1", "redis1:70000", "redis1,"}) {
            Map<String, String> values = settings("grantforge.redis.nodes", wrong);
            assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(values, "test")).withMessageContaining("redis.nodes");
        }
    }

    @Test
    void checksTheTlsSettings() throws IOException
    {
        Map<String, String> mode = settings("grantforge.redis.tls.mode", "required");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(mode, "test")).withMessageContaining("required");
        Map<String, String> plain = settings("grantforge.redis.tls.ca.file", "/ca.pem");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(plain, "test")).withMessageContaining("verify-ca and verify-full mode only");
        Map<String, String> keyAlone = settings("grantforge.redis.tls.mode", "verify-ca", "grantforge.redis.tls.key.file", "/k.pem");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(keyAlone, "test")).withMessageContaining("redis.tls.certificate.file");
    }
}
