// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.devlive.grantforge.agent.AgentSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsTest
{
    @TempDir
    Path directory;

    static Map<String, String> minimal(Path directory) throws IOException
    {
        Map<String, String> values = new HashMap<>();
        values.put("grantforge.server.url", "https://grantforge.example.com");
        values.put("grantforge.token.file", Files.writeString(directory.resolve("token"), "gfa_token\n").toString());
        values.put("grantforge.instance", "test-1");
        values.put("grantforge.cache.dir", directory.resolve("cache").toString());
        return values;
    }

    private Settings settings(String... more) throws IOException
    {
        Map<String, String> values = minimal(directory);
        for (int i = 0; i < more.length; i += 2) {
            values.put(more[i], more[i + 1]);
        }
        return new Settings(values, "Test");
    }

    @Test
    void readsTheGrantForgeAgentWithItsDefaults() throws IOException
    {
        AgentSettings agent = settings().agent("2026.4.0");
        assertThat(agent.server()).isEqualTo(URI.create("https://grantforge.example.com"));
        assertThat(agent.token()).isEqualTo("gfa_token");
        assertThat(agent.instance()).isEqualTo("test-1");
        assertThat(agent.cacheDirectory()).isEqualTo(directory.resolve("cache"));
        assertThat(agent.agentVersion()).isEqualTo("2026.4.0");
        assertThat(agent.trustedKey()).isNull();
        assertThat(agent.auditBatchSize()).isEqualTo(500);
        assertThat(agent.refreshInterval()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void readsTheSigningKeyAndTheAuditBatches() throws IOException, NoSuchAlgorithmException
    {
        String key = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        AgentSettings agent = settings("grantforge.signing.key.file", Files.writeString(directory.resolve("key"), key).toString(),
                "grantforge.audit.batch.size", "50", "grantforge.audit.queue.capacity", "50").agent("1");
        assertThat(agent.trustedKey()).isNotNull();
        assertThat(agent.auditBatchSize()).isEqualTo(50);
    }

    @Test
    void refusesAnAgentThatCannotRun() throws IOException
    {
        assertThatThrownBy(() -> settings().agent(" ")).hasMessage("the agent version must be 1 to 64 characters");
        assertThatThrownBy(() -> settings().agent("x".repeat(65))).hasMessage("the agent version must be 1 to 64 characters");
        assertThatThrownBy(() -> settings("grantforge.audit.batch.size", "100", "grantforge.audit.queue.capacity", "10").agent("1"))
                .hasMessage("grantforge.audit.queue.capacity must be at least grantforge.audit.batch.size");
        Map<String, String> values = minimal(directory);
        values.remove("grantforge.instance");
        assertThatThrownBy(() -> new Settings(values, "Test").agent("1")).hasMessage("missing Test agent setting grantforge.instance");
        assertThatThrownBy(() -> settings("grantforge.token.file", directory.resolve("none").toString()).agent("1"))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void readsTheSyncAndTheFoldWindow() throws IOException
    {
        assertThat(settings().syncInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings().foldWindow()).isEqualTo(Duration.ofSeconds(10));
        Settings set = settings("grantforge.sync.interval.ms", "60000", "grantforge.audit.fold.window.ms", "0");
        assertThat(set.syncInterval()).isEqualTo(Duration.ofMinutes(1));
        assertThat(set.foldWindow()).isZero();
        assertThatThrownBy(() -> settings("grantforge.sync.interval.ms", "10").syncInterval())
                .hasMessage("grantforge.sync.interval.ms must be between 1000 and 3600000");
    }

    @Test
    void readsTheRolePrefixTheSystemAccepts() throws IOException
    {
        Pattern valid = Pattern.compile("[a-z]+_");
        assertThat(settings().rolePrefix("test.role.prefix", valid.asMatchPredicate(), "be lower case")).isEqualTo("grantforge_");
        assertThat(settings("grantforge.test.role.prefix", " gf_ ").rolePrefix("test.role.prefix", valid.asMatchPredicate(), "be lower case"))
                .isEqualTo("gf_");
        assertThatThrownBy(() -> settings("grantforge.test.role.prefix", "GF").rolePrefix("test.role.prefix", valid.asMatchPredicate(),
                "be lower case")).hasMessage("grantforge.test.role.prefix must be lower case");
    }

    @Test
    void readsTextSwitchesNumbersAndNames() throws IOException
    {
        Settings set = settings("grantforge.blank", "  ", "grantforge.on", " TRUE ", "grantforge.off", "yes", "grantforge.count", " 7 ",
                "grantforge.users", " etl, , backup ,etl", "grantforge.word", "not a number");
        assertThat(set.optional("blank")).isNull();
        assertThat(set.optional("missing")).isNull();
        assertThat(set.optional("count")).isEqualTo("7");
        assertThat(set.text("blank", "fallback")).isEmpty();
        assertThat(set.text("missing", " fallback ")).isEqualTo("fallback");
        assertThat(set.flag("on", false)).isTrue();
        assertThat(set.flag("off", true)).isFalse();
        assertThat(set.flag("missing", true)).isTrue();
        assertThat(set.number("count", 1, 0, 10)).isEqualTo(7);
        assertThat(set.number("missing", 3, 0, 10)).isEqualTo(3);
        assertThat(set.millis("count", 1, 0, 10)).isEqualTo(Duration.ofMillis(7));
        assertThat(set.names("users")).containsExactly("etl", "backup");
        assertThat(set.names("missing")).isEmpty();
        assertThatThrownBy(() -> set.number("word", 1, 0, 10)).hasMessage("grantforge.word must be an integer");
        assertThatThrownBy(() -> set.number("count", 1, 8, 10)).hasMessage("grantforge.count must be between 8 and 10");
        assertThatThrownBy(() -> set.required("blank")).hasMessage("missing Test agent setting grantforge.blank");
    }

    @Test
    void readsTheFilesSettingsName() throws IOException
    {
        Path file = Files.writeString(directory.resolve("password"), "geheim\n", StandardCharsets.UTF_8);
        assertThat(Settings.read(file.toString(), StandardCharsets.UTF_8)).isEqualTo("geheim\n");
    }
}
