// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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
        values.put("grantforge.instance", "elasticsearch-1");
        values.put("grantforge.cache.dir", directory.resolve("cache").toString());
        values.put("grantforge.elasticsearch.hosts", "ES1.example.com, https://es2:9201, https://es1.example.com:9200/, [::1]");
        values.put("grantforge.elasticsearch.user", "grantforge");
        values.put("grantforge.elasticsearch.password.file", Files.writeString(directory.resolve("password"), " secret \n").toString());
        return values;
    }

    private AgentConfig parse(@Nullable String... more) throws IOException
    {
        Map<String, String> values = minimal(directory);
        for (int index = 0; index < more.length; index += 2) {
            if (more[index + 1] == null) {
                values.remove(String.valueOf(more[index]));
            }
            else {
                values.put(String.valueOf(more[index]), more[index + 1]);
            }
        }
        return AgentConfig.parse(values, "2026.3.0");
    }

    @Test
    void readsTheSettingsWithTheirDefaults() throws IOException
    {
        AgentConfig config = parse();
        assertThat(config.connection().hosts()).containsExactly("https://es1.example.com:9200", "https://es2:9201", "https://[::1]:9200");
        assertThat(config.connection().user()).isEqualTo("grantforge");
        assertThat(config.connection().password()).isEqualTo("secret");
        assertThat(config.connection().verificationMode()).isEqualTo("full");
        assertThat(config.connection().ca()).isNull();
        assertThat(config.connection().timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.connection().toString()).doesNotContain("secret");
        assertThat(config.rolePrefix()).isEqualTo("grantforge_");
        assertThat(config.unmanaged()).isEmpty();
        assertThat(config.syncInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.foldWindow()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.connection().apiKey()).isNull();
        assertThat(config.auditLog()).as("the agent reads audit files only when told which").isNull();
        assertThat(config.agent().token()).isEqualTo("gfa_token");
        assertThat(config.agent().trustedKey()).isNull();
    }

    @Test
    void readsACertificateSignInAndUsersToLeaveAlone() throws IOException, NoSuchAlgorithmException
    {
        String key = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        AgentConfig config = parse("grantforge.elasticsearch.user", null, "grantforge.elasticsearch.password.file", null,
                "grantforge.elasticsearch.ssl.verification.mode", "Certificate", "grantforge.elasticsearch.tls.ca", "/etc/ca.pem",
                "grantforge.elasticsearch.tls.cert", "/etc/agent.pem", "grantforge.elasticsearch.tls.key", "/etc/agent-key.pem",
                "grantforge.elasticsearch.tls.key.passphrase.file", Files.writeString(directory.resolve("phrase"), "phrase\n").toString(),
                "grantforge.elasticsearch.unmanaged.users", " etl, , backup ", "grantforge.elasticsearch.role.prefix", "gf-es_",
                "grantforge.audit.log.files", "/var/log/es/a_audit.json, /mnt/b_audit.json", "grantforge.audit.log.max.lines", "10",
                "grantforge.audit.fold.window.ms", "0",
                "grantforge.signing.key.file", Files.writeString(directory.resolve("key"), key).toString(), "grantforge.elasticsearch.timeout.ms", "5");
        assertThat(config.connection().user()).isNull();
        assertThat(config.connection().certificate()).isEqualTo("/etc/agent.pem");
        assertThat(config.connection().key()).isEqualTo("/etc/agent-key.pem");
        assertThat(config.connection().keyPassphrase()).isEqualTo("phrase");
        assertThat(config.connection().verificationMode()).isEqualTo("certificate");
        assertThat(config.connection().ca()).isEqualTo("/etc/ca.pem");
        assertThat(config.connection().timeout()).isEqualTo(Duration.ofMillis(5));
        assertThat(config.unmanaged()).containsExactlyInAnyOrder("etl", "backup");
        assertThat(config.rolePrefix()).isEqualTo("gf-es_");
        assertThat(config.auditLog()).isEqualTo(new AgentConfig.AuditFiles(List.of(Path.of("/var/log/es/a_audit.json"), Path.of("/mnt/b_audit.json")),
                Duration.ofSeconds(5), 10));
        assertThat(config.foldWindow()).isZero();
        assertThat(config.agent().trustedKey()).isNotNull();
        assertThat(parse("grantforge.elasticsearch.hosts", "http://es1").connection().hosts()).containsExactly("http://es1:9200");
    }

    @Test
    void readsAnApiKeySignIn() throws IOException
    {
        AgentConfig config = parse("grantforge.elasticsearch.user", null, "grantforge.elasticsearch.password.file", null,
                "grantforge.elasticsearch.api.key.file", Files.writeString(directory.resolve("key"), " VnVhQ2ZHY0I6dWkybHA= \n").toString());
        assertThat(config.connection().apiKey()).isEqualTo("VnVhQ2ZHY0I6dWkybHA=");
        assertThat(config.connection().user()).isNull();
        assertThat(config.connection().toString()).doesNotContain("VnVh");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://es1", "es1:0", "es1:70000", "https://es1/path", "user@es1", "http://es1,https://es2"})
    void refusesWhatIsNoListOfNodes(String hosts)
    {
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.hosts", hosts));
    }

    @Test
    void refusesSignInsAndTlsSettingsThatDoNotFit()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.password.file", null)).withMessageContaining("go together");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.user", null, "grantforge.elasticsearch.password.file", null))
                .withMessageContaining("signs in one way");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.tls.cert", "/c.pem", "grantforge.elasticsearch.tls.key", "/k.pem"))
                .withMessageContaining("signs in one way");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.api.key.file", "/key")).withMessageContaining("signs in one way");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.tls.cert", "/c.pem")).withMessageContaining("go together");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.hosts", "http://es1", "grantforge.elasticsearch.tls.ca", "/ca.pem"))
                .withMessageContaining("https://");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.ssl.verification.mode", "none", "grantforge.elasticsearch.tls.ca",
                "/ca.pem")).withMessageContaining("checks nothing");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.ssl.verification.mode", "strict"));
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.elasticsearch.tls.key.passphrase.file", "/p"))
                .withMessageContaining("passphrase");
    }

    @Test
    void refusesOtherSettingsOutsideTheirRange()
    {
        for (String prefix : new String[] {"Grantforge_", "gf", "_gf_", "gf.x_", "a234567890123456789012345678901234_"}) {
            assertThatIllegalArgumentException().as(prefix).isThrownBy(() -> parse("grantforge.elasticsearch.role.prefix", prefix));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.audit.log.files", "/a.json", "grantforge.audit.log.max.lines", "1"));
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.sync.interval.ms", "10"));
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.sync.interval.ms", "soon")).withMessageContaining("integer");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.audit.batch.size", "500", "grantforge.audit.queue.capacity", "10"));
        assertThatIllegalArgumentException().isThrownBy(() -> parse("grantforge.instance", " ")).withMessageContaining("grantforge.instance");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(minimal(directory), " "));
    }
}
