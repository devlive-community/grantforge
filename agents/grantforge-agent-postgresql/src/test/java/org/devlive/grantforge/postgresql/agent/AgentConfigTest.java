// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

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
        values.put("grantforge.instance", "postgresql-1");
        values.put("grantforge.cache.dir", directory.resolve("cache").toString());
        values.put("grantforge.postgresql.hosts", "PG1.example.com, pg2:6432, pg1.example.com:5432, [::1]");
        values.put("grantforge.postgresql.user", "grantforge");
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
        AgentConfig config = parse("grantforge.postgresql.password.file", Files.writeString(directory.resolve("password"), " secret \n").toString());
        assertThat(config.connection().hosts()).containsExactly("pg1.example.com:5432", "pg2:6432", "[::1]:5432");
        assertThat(config.connection().database()).isEqualTo("postgres");
        assertThat(config.connection().user()).isEqualTo("grantforge");
        assertThat(config.connection().password()).isEqualTo("secret");
        assertThat(config.connection().sslMode()).isEqualTo("prefer");
        assertThat(config.connection().timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.connection().toString()).doesNotContain("secret");
        assertThat(config.rolePrefix()).isEqualTo("grantforge_");
        assertThat(config.unmanaged()).isEmpty();
        assertThat(config.databases()).isEmpty();
        assertThat(config.syncInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.foldWindow()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.serverLog()).isEqualTo(new AgentConfig.ServerLog(Duration.ofSeconds(5), 16L * 1024 * 1024));
        assertThat(config.agent().token()).isEqualTo("gfa_token");
        assertThat(config.agent().trustedKey()).isNull();
    }

    @Test
    void readsCertificatesDatabasesAndUsersToLeaveAlone() throws IOException, NoSuchAlgorithmException
    {
        String key = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        AgentConfig config = parse("grantforge.postgresql.ssl.mode", "VERIFY-FULL", "grantforge.postgresql.tls.ca", "/etc/ca.pem",
                "grantforge.postgresql.tls.cert", "/etc/agent.pem", "grantforge.postgresql.tls.key", "/etc/agent.key",
                "grantforge.postgresql.unmanaged.users", " etl, , backup ", "grantforge.postgresql.role.prefix", "gf_",
                "grantforge.postgresql.databases", "sales, hr", "grantforge.postgresql.database", "admin",
                "grantforge.audit.server.log.max.bytes", "65536", "grantforge.audit.fold.window.ms", "0",
                "grantforge.signing.key.file", Files.writeString(directory.resolve("key"), key).toString());
        assertThat(config.connection().certificate()).isEqualTo("/etc/agent.pem");
        assertThat(config.connection().key()).isEqualTo("/etc/agent.key");
        assertThat(config.connection().ca()).isEqualTo("/etc/ca.pem");
        assertThat(config.connection().sslMode()).isEqualTo("verify-full");
        assertThat(config.connection().password()).isNull();
        assertThat(config.connection().database()).isEqualTo("admin");
        assertThat(config.unmanaged()).containsExactlyInAnyOrder("etl", "backup");
        assertThat(config.databases()).containsExactlyInAnyOrder("sales", "hr");
        assertThat(config.rolePrefix()).isEqualTo("gf_");
        assertThat(config.serverLog()).isNotNull().extracting(AgentConfig.ServerLog::maxBytes).isEqualTo(65536L);
        assertThat(config.foldWindow()).isZero();
        assertThat(config.agent().trustedKey()).isNotNull();
        assertThat(parse("grantforge.audit.server.log", "false").serverLog()).isNull();
        assertThat(parse("grantforge.postgresql.ssl.mode", "require", "grantforge.postgresql.tls.cert", "/c.pem", "grantforge.postgresql.tls.key",
                "/k.pem").connection().ca()).as("TLS without checking the server").isNull();
    }

    @Test
    void refusesWhatDoesNotFit() throws IOException
    {
        Map<String, String> missing = minimal(directory);
        missing.remove("grantforge.postgresql.user");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(missing, "1")).withMessageContaining("grantforge.postgresql.user");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(minimal(directory), " ")).withMessageContaining("version");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.role.prefix", "grantforge"))
                .withMessageContaining("end with an underscore");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.role.prefix", "1x_")).withMessageContaining("start with a letter");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.role.prefix", "PG_x_")).withMessageContaining("pg_");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.ssl.mode", "on")).withMessageContaining("one of");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.ssl.mode", "require", "grantforge.postgresql.tls.cert",
                "/c.pem")).withMessageContaining("go together");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.ssl.mode", "require", "grantforge.postgresql.tls.cert",
                "/c.pem", "grantforge.postgresql.tls.key", "/k.pem", "grantforge.postgresql.password.file", "/p")).withMessageContaining("not both");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.tls.cert", "/c.pem", "grantforge.postgresql.tls.key",
                "/k.pem")).withMessageContaining("require, verify-ca or verify-full");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.tls.ca", "/ca.pem")).withMessageContaining("verify-ca");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.postgresql.ssl.mode", "verify-ca", "grantforge.postgresql.tls.cert",
                "/c.pem", "grantforge.postgresql.tls.key", "/k.pem")).withMessageContaining("needs grantforge.postgresql.tls.ca");
        for (String host : new String[] {"pg1:0", "pg1:70000", "pg1:", "jdbc:postgresql://pg1", "pg 1", "-pg1"}) {
            assertThatIllegalArgumentException().as(host).isThrownBy(() -> parse("grantforge.postgresql.hosts", host))
                    .withMessageContaining("grantforge.postgresql.hosts");
        }
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.sync.interval.ms", "999")).withMessageContaining("between 1000");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.sync.interval.ms", "often")).withMessageContaining("integer");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.audit.batch.size", "600", "grantforge.audit.queue.capacity", "500"))
                .withMessageContaining("at least");
    }
}
