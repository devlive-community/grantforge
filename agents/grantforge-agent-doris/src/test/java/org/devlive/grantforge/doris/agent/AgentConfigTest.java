// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

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
                "grantforge.instance", "doris-1", "grantforge.cache.dir", directory.resolve("cache").toString(), "grantforge.doris.hosts", "fe1:9030",
                "grantforge.doris.user", "grantforge"));
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
        assertThat(config.connection().hosts()).containsExactly("fe1:9030");
        assertThat(config.connection().user()).isEqualTo("grantforge");
        assertThat(config.connection().password()).isNull();
        assertThat(config.connection().sslMode()).isEqualTo("disable");
        assertThat(config.connection().ca()).isNull();
        assertThat(config.connection().timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.rolePrefix()).isEqualTo("grantforge_");
        assertThat(config.unmanaged()).isEmpty();
        assertThat(config.catalogs()).as("every catalog").isEmpty();
        AgentConfig.AuditLog log = config.auditLog();
        assertThat(log).isNotNull();
        assertThat(log.delay()).as("the cluster's own batch interval").isNull();
        assertThat(log.interval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(log.batch()).isEqualTo(1000);
        assertThat(config.connection().toString()).contains("fe1:9030").doesNotContain("password=");
    }

    @Test
    void readsEverySetting() throws IOException
    {
        Path password = Files.writeString(directory.resolve("password"), " secret \n", StandardCharsets.UTF_8);
        AgentConfig config = AgentConfig.parse(settings("grantforge.doris.hosts", "FE1, fe2:19030,fe1", "grantforge.doris.password.file",
                password.toString(), "grantforge.doris.ssl.mode", "Verify-Full", "grantforge.doris.tls.ca", "/etc/ca.pem",
                "grantforge.doris.timeout.ms", "5000", "grantforge.doris.role.prefix", "gf_", "grantforge.doris.unmanaged.users", "etl, svc",
                "grantforge.doris.catalogs", "internal,hive", "grantforge.audit.log.delay.ms", "2000", "grantforge.audit.log.interval.ms", "1000",
                "grantforge.audit.log.batch", "10"), "test");
        assertThat(config.connection().hosts()).containsExactly("fe1:9030", "fe2:19030");
        assertThat(config.connection().password()).isEqualTo("secret");
        assertThat(config.connection().sslMode()).isEqualTo("verify-full");
        assertThat(config.connection().ca()).isEqualTo("/etc/ca.pem");
        assertThat(config.connection().timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.rolePrefix()).isEqualTo("gf_");
        assertThat(config.unmanaged()).containsExactlyInAnyOrder("etl", "svc");
        assertThat(config.catalogs()).containsExactlyInAnyOrder("internal", "hive");
        AgentConfig.AuditLog log = config.auditLog();
        assertThat(log).isNotNull();
        assertThat(log.delay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(log.batch()).isEqualTo(10);
    }

    @Test
    void turnsTheAuditLogOff() throws IOException
    {
        assertThat(AgentConfig.parse(settings("grantforge.audit.log", "false"), "test").auditLog()).isNull();
    }

    @Test
    void refusesWhatIsNoFrontend()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(settings("grantforge.doris.hosts", "jdbc:mysql://fe1"), "test"))
                .withMessageContaining("grantforge.doris.hosts");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(settings("grantforge.doris.hosts", "fe1:70000"), "test"));
    }

    @Test
    void refusesAnUnknownTlsModeAndACertificateNothingVerifies()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(settings("grantforge.doris.ssl.mode", "required"), "test"))
                .withMessageContaining("grantforge.doris.ssl.mode");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(settings("grantforge.doris.ssl.mode", "trust",
                "grantforge.doris.tls.ca", "/ca.pem"), "test")).withMessageContaining("grantforge.doris.tls.ca");
    }

    @Test
    void refusesARolePrefixThatIsNoPlainName()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(settings("grantforge.doris.role.prefix", "gf-"), "test"))
                .withMessageContaining("grantforge.doris.role.prefix");
    }
}
