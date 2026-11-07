// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

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

    /** The settings an agent needs and no more, its token and password written into a directory. */
    static Map<String, String> minimal(Path directory) throws IOException
    {
        Path token = Files.writeString(directory.resolve("token"), "gfa_token", StandardCharsets.UTF_8);
        Path password = Files.writeString(directory.resolve("password"), " agent-secret\n", StandardCharsets.UTF_8);
        return new HashMap<>(Map.of("grantforge.server.url", "https://grantforge.example.com", "grantforge.token.file", token.toString(),
                "grantforge.instance", "mongodb-1", "grantforge.cache.dir", directory.resolve("cache").toString(), "grantforge.mongodb.uri", "mongodb://db1,db2/?replicaSet=rs0",
                "grantforge.mongodb.user", "grantforge", "grantforge.mongodb.password.file", password.toString()));
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
        assertThat(connection.uri()).isEqualTo("mongodb://db1,db2/?replicaSet=rs0");
        assertThat(connection.user()).isEqualTo("grantforge");
        assertThat(connection.password()).isEqualTo("agent-secret");
        assertThat(connection.mechanism()).isEqualTo(AgentConfig.SCRAM_SHA_256);
        assertThat(connection.source()).isEqualTo("admin");
        assertThat(connection.serviceName()).isEqualTo("mongodb");
        assertThat(connection.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(connection.toString()).doesNotContain("agent-secret");
        assertThat(config.rolePrefix()).isEqualTo("grantforge_");
        assertThat(config.unmanaged()).isEmpty();
        assertThat(config.databases()).isEmpty();
        AgentConfig.Audit audit = config.audit();
        assertThat(audit).isNotNull();
        assertThat(audit.files()).as("no files reads the profiler").isEmpty();
        assertThat(audit.delay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(audit.interval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(audit.batch()).isEqualTo(1000);
    }

    @Test
    void readsEverySetting() throws IOException
    {
        Path keyPassword = Files.writeString(directory.resolve("key-password"), "key-secret\n");
        AgentConfig config = AgentConfig.parse(settings("grantforge.mongodb.uri", "mongodb://db1/?tls=true", "grantforge.mongodb.auth.mechanism", "scram-sha-1",
                "grantforge.mongodb.auth.source", "other", "grantforge.mongodb.tls.ca.file", "/ca.pem", "grantforge.mongodb.tls.certificate.key.file", "/client.pem",
                "grantforge.mongodb.tls.certificate.key.password.file", keyPassword.toString(), "grantforge.mongodb.kerberos.service.name", "mongo",
                "grantforge.mongodb.timeout.ms", "7000", "grantforge.mongodb.role.prefix", "gf_", "grantforge.mongodb.unmanaged.users", "etl, svc",
                "grantforge.mongodb.databases", "sales", "grantforge.mongodb.audit.files", "/log/b.json,/log/a.json", "grantforge.audit.log.delay.ms", "0",
                "grantforge.audit.log.interval.ms", "1000", "grantforge.audit.log.batch", "5"), "test");
        AgentConfig.Connection connection = config.connection();
        assertThat(connection.mechanism()).isEqualTo(AgentConfig.SCRAM_SHA_1);
        assertThat(connection.source()).isEqualTo("other");
        assertThat(connection.caFile()).isEqualTo("/ca.pem");
        assertThat(connection.certificateKeyFile()).isEqualTo("/client.pem");
        assertThat(connection.certificateKeyPassword()).isEqualTo("key-secret");
        assertThat(connection.serviceName()).isEqualTo("mongo");
        assertThat(connection.timeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(config.rolePrefix()).isEqualTo("gf_");
        assertThat(config.unmanaged()).containsExactlyInAnyOrder("etl", "svc");
        assertThat(config.databases()).containsExactly("sales");
        AgentConfig.Audit audit = config.audit();
        assertThat(audit).isNotNull();
        assertThat(audit.files()).containsExactly(Path.of("/log/a.json"), Path.of("/log/b.json"));
        assertThat(audit.delay()).isZero();
        assertThat(audit.batch()).isEqualTo(5);
    }

    @Test
    void theAccessReadsCanBeTurnedOff() throws IOException
    {
        assertThat(AgentConfig.parse(settings("grantforge.audit.log", "false"), "test").audit()).isNull();
    }

    @Test
    void wantsAConnectionStringWithoutCredentials() throws IOException
    {
        Map<String, String> broken = settings("grantforge.mongodb.uri", "http://hunter2@db1");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(broken, "test")).withMessageContaining("connection string").withMessageNotContaining("hunter2");
        Map<String, String> embedded = settings("grantforge.mongodb.uri", "mongodb://agent:hunter2@db1/");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(embedded, "test")).withMessageContaining("holds no credentials").withMessageNotContaining("hunter2");
        Map<String, String> missing = settings();
        missing.remove("grantforge.mongodb.uri");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(missing, "test")).withMessageContaining("mongodb.uri");
    }

    @Test
    void checksHowTheAgentSignsIn() throws IOException
    {
        Map<String, String> unknown = settings("grantforge.mongodb.auth.mechanism", "MONGODB-CR");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(unknown, "test")).withMessageContaining("MONGODB-CR");
        Map<String, String> noUser = settings();
        noUser.remove("grantforge.mongodb.user");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(noUser, "test")).withMessageContaining("mongodb.user");
        Map<String, String> noPassword = settings();
        noPassword.remove("grantforge.mongodb.password.file");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(noPassword, "test")).withMessageContaining("mongodb.password.file");
        Map<String, String> plain = settings("grantforge.mongodb.auth.mechanism", "PLAIN");
        plain.remove("grantforge.mongodb.password.file");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(plain, "test")).withMessageContaining("with PLAIN");
    }

    @Test
    void aClientCertificateSignsInWithoutAUser() throws IOException
    {
        Map<String, String> values = settings("grantforge.mongodb.uri", "mongodb://db1/?tls=true", "grantforge.mongodb.auth.mechanism", "MONGODB-X509");
        values.remove("grantforge.mongodb.user");
        values.remove("grantforge.mongodb.password.file");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(values, "test")).withMessageContaining("mongodb.tls.certificate.key.file");
        values.put("grantforge.mongodb.tls.certificate.key.file", "/client.pem");
        assertThat(AgentConfig.parse(values, "test").connection().user()).isNull();
    }

    @Test
    void kerberosTakesAKeytabOrAPassword() throws IOException
    {
        Map<String, String> values = settings("grantforge.mongodb.auth.mechanism", "GSSAPI");
        values.remove("grantforge.mongodb.password.file");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(values, "test")).withMessageContaining("mongodb.kerberos.keytab");
        values.put("grantforge.mongodb.kerberos.keytab", "/agent.keytab");
        assertThat(AgentConfig.parse(values, "test").connection().keytab()).isEqualTo("/agent.keytab");
    }

    @Test
    void tlsFilesWantTlsInTheConnectionString() throws IOException
    {
        Map<String, String> values = settings("grantforge.mongodb.tls.ca.file", "/ca.pem");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(values, "test")).withMessageContaining("tls=true");
    }

    @Test
    void refusesARolePrefixThatIsNoPlainName() throws IOException
    {
        Map<String, String> values = settings("grantforge.mongodb.role.prefix", "bad prefix");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentConfig.parse(values, "test")).withMessageContaining("mongodb.role.prefix");
    }
}
