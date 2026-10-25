// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgentConfigTest
{
    @TempDir
    Path directory;

    /** The least an agent needs, in a directory for its files. */
    static Map<String, String> minimal(Path directory) throws IOException
    {
        Map<String, String> values = new HashMap<>();
        values.put("grantforge.server.url", "https://grantforge.example.com");
        values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), "gfa_token\n").toString());
        values.put("grantforge.instance", "clickhouse-1");
        values.put("grantforge.cache.dir", directory.resolve("cache").toString());
        values.put("grantforge.clickhouse.urls", "https://ch1.example.com:8443/, HTTP://ch2:8123");
        values.put("grantforge.clickhouse.user", "grantforge");
        return values;
    }

    private AgentConfig parse(String... more) throws IOException
    {
        Map<String, String> values = minimal(directory);
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return AgentConfig.parse(values, "2026.3.0");
    }

    @Test
    void readsTheSettingsWithTheirDefaults() throws IOException
    {
        AgentConfig config = parse("grantforge.clickhouse.password.file", Files.writeString(directory.resolve("password"), " secret \n").toString());
        assertThat(config.connection().urls()).containsExactly("https://ch1.example.com:8443", "http://ch2:8123");
        assertThat(config.connection().user()).isEqualTo("grantforge");
        assertThat(config.connection().password()).isEqualTo("secret");
        assertThat(config.connection().timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.connection().toString()).doesNotContain("secret");
        assertThat(config.rolePrefix()).isEqualTo("grantforge_");
        assertThat(config.unmanaged()).isEmpty();
        assertThat(config.syncInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.foldWindow()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.queryLog()).isEqualTo(new AgentConfig.QueryLog(null, Duration.ofSeconds(10), Duration.ofSeconds(5), 1000));
        assertThat(config.agent().token()).isEqualTo("gfa_token");
        assertThat(config.agent().trustedKey()).isNull();
    }

    @Test
    void readsCertificatesUsersToLeaveAloneAndTheQueryLogsCluster() throws IOException, NoSuchAlgorithmException
    {
        String key = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        AgentConfig config = parse("grantforge.clickhouse.urls", "https://ch1:8443", "grantforge.clickhouse.tls.ca", "/etc/ca.pem",
                "grantforge.clickhouse.tls.cert", "/etc/agent.pem", "grantforge.clickhouse.tls.key", "/etc/agent.key",
                "grantforge.clickhouse.unmanaged.users", " etl, , backup ", "grantforge.clickhouse.role.prefix", "gf_",
                "grantforge.audit.query.log.cluster", "main", "grantforge.audit.query.log.delay.ms", "0", "grantforge.audit.fold.window.ms", "0",
                "grantforge.signing.key.file", Files.writeString(directory.resolve("key"), key).toString());
        assertThat(config.connection().certificate()).isEqualTo("/etc/agent.pem");
        assertThat(config.connection().key()).isEqualTo("/etc/agent.key");
        assertThat(config.connection().ca()).isEqualTo("/etc/ca.pem");
        assertThat(config.connection().password()).isNull();
        assertThat(config.unmanaged()).containsExactlyInAnyOrder("etl", "backup");
        assertThat(config.rolePrefix()).isEqualTo("gf_");
        assertThat(config.queryLog()).isNotNull().extracting(AgentConfig.QueryLog::cluster).isEqualTo("main");
        assertThat(config.foldWindow()).isZero();
        assertThat(config.agent().trustedKey()).isNotNull();
        assertThat(parse("grantforge.audit.query.log", "false").queryLog()).isNull();
    }

    @Test
    void refusesWhatDoesNotFit() throws IOException
    {
        Map<String, String> missing = minimal(directory);
        missing.remove("grantforge.clickhouse.user");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(missing, "1")).withMessageContaining("grantforge.clickhouse.user");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(minimal(directory), " ")).withMessageContaining("version");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.clickhouse.role.prefix", "grantforge"))
                .withMessageContaining("end with an underscore");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.clickhouse.role.prefix", "1x_")).withMessageContaining("start with a letter");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.clickhouse.urls", "https://ch1:8443",
                "grantforge.clickhouse.tls.cert", "/c.pem")).withMessageContaining("go together");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.clickhouse.urls", "https://ch1:8443", "grantforge.clickhouse.tls.cert",
                "/c.pem", "grantforge.clickhouse.tls.key", "/k.pem", "grantforge.clickhouse.password.file", "/p")).withMessageContaining("not both");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.clickhouse.tls.ca", "/ca.pem")).withMessageContaining("https://");
        for (String url : new String[] {"ch1:8123", "http://ch1:8123/path", "http://ch1?x", "http://u@ch1", "ftp://ch1", "http://"}) {
            assertThatIllegalArgumentException().as(url).isThrownBy(() -> parse("grantforge.clickhouse.urls", url))
                    .withMessageContaining("grantforge.clickhouse.urls");
        }
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.clickhouse.urls", "http://a b"))
                .withMessageContaining("which is no URL");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.sync.interval.ms", "999")).withMessageContaining("between 1000");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.sync.interval.ms", "often")).withMessageContaining("integer");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.audit.batch.size", "600", "grantforge.audit.queue.capacity", "500"))
                .withMessageContaining("at least");
    }
}
