// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HiveAgentRuntimeTest
{
    @TempDir
    Path temporary;

    private final GrantForgeAgent agent = mock(GrantForgeAgent.class);
    @SuppressWarnings("unchecked")
    private final Function<AgentSettings, GrantForgeAgent> factory = mock(Function.class);
    private final HiveAgentRuntime runtime = new HiveAgentRuntime(factory);

    @BeforeEach
    void usesOnlyTheInjectedAgent()
    {
        when(factory.apply(any(AgentSettings.class))).thenReturn(agent);
    }

    @AfterEach
    void closesRuntime()
    {
        runtime.close();
    }

    private Map<String, String> settings() throws IOException
    {
        Path token = temporary.resolve("token");
        Files.writeString(token, " token\n", StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        values.put("grantforge.token.file", token.toString());
        values.put("grantforge.server.url", "https://grantforge.example.com/");
        values.put("grantforge.instance", "hs2-1");
        values.put("grantforge.cache.dir", temporary.resolve("cache").toString());
        return values;
    }

    @Test
    void readsRequiredSettingsAndDefaults() throws IOException
    {
        Map<String, String> values = settings();
        values.replaceAll((key, value) -> " " + value + " \n");
        assertThat(runtime.start(values, "2026.2.0")).isTrue();

        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory).apply(parsed.capture());
        AgentSettings actual = parsed.getValue();
        assertThat(actual.server()).isEqualTo(URI.create("https://grantforge.example.com/"));
        assertThat(actual.token()).isEqualTo("token");
        assertThat(actual.instance()).isEqualTo("hs2-1");
        assertThat(actual.cacheDirectory()).isEqualTo(temporary.resolve("cache"));
        assertThat(actual.agentVersion()).isEqualTo("2026.2.0");
        assertThat(actual.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(actual.readTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(actual.refreshInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(actual.auditBatchSize()).isEqualTo(500);
        assertThat(actual.auditQueueCapacity()).isEqualTo(10000);
        assertThat(actual.auditFlushInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(actual.spoolLimitBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(actual.trustedKey()).isNull();
    }

    @Test
    void readsConfiguredNumbersAndAPinnedSigningKey() throws Exception
    {
        Map<String, String> values = settings();
        values.put("grantforge.connect.timeout.ms", " 400 ");
        values.put("grantforge.read.timeout.ms", "600");
        values.put("grantforge.refresh.interval.ms", "2000");
        values.put("grantforge.audit.batch.size", "17");
        values.put("grantforge.audit.queue.capacity", "42");
        values.put("grantforge.audit.flush.interval.ms", "200");
        values.put("grantforge.audit.spool.limit.bytes", "0");
        String encoded = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        Path key = temporary.resolve("key");
        Files.writeString(key, " " + encoded + "\n", StandardCharsets.US_ASCII);
        values.put("grantforge.signing.key.file", " " + key + " ");
        runtime.start(values, "version");

        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory).apply(parsed.capture());
        AgentSettings actual = parsed.getValue();
        assertThat(actual.connectTimeout()).isEqualTo(Duration.ofMillis(400));
        assertThat(actual.readTimeout()).isEqualTo(Duration.ofMillis(600));
        assertThat(actual.refreshInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(actual.auditBatchSize()).isEqualTo(17);
        assertThat(actual.auditQueueCapacity()).isEqualTo(42);
        assertThat(actual.auditFlushInterval()).isEqualTo(Duration.ofMillis(200));
        assertThat(actual.spoolLimitBytes()).isZero();
        assertThat(actual.trustedKey()).isNotNull();
    }

    @Test
    void refusesMissingAndInvalidSettingsBeforeCreatingTheAgent() throws IOException
    {
        Map<String, String> baseline = settings();
        for (String key : List.of("token.file", "server.url", "instance", "cache.dir")) {
            Map<String, String> missing = new HashMap<>(baseline);
            missing.put("grantforge." + key, " ");
            assertThatThrownBy(() -> runtime.start(missing, "version")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing Hive agent setting grantforge." + key);
            missing.remove("grantforge." + key);
            assertThatThrownBy(() -> runtime.start(missing, "version")).isInstanceOf(IllegalArgumentException.class);
        }
        for (Map.Entry<String, String> invalid : Map.of("connect.timeout.ms", "0", "refresh.interval.ms", "999", "audit.batch.size", "x",
                "audit.queue.capacity", "1000001", "audit.spool.limit.bytes", "-1").entrySet()) {
            Map<String, String> values = new HashMap<>(baseline);
            values.put("grantforge." + invalid.getKey(), invalid.getValue());
            assertThatThrownBy(() -> runtime.start(values, "version")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("grantforge." + invalid.getKey());
        }
        Map<String, String> small = new HashMap<>(baseline);
        small.put("grantforge.audit.batch.size", "17");
        small.put("grantforge.audit.queue.capacity", "16");
        assertThatThrownBy(() -> runtime.start(small, "version")).hasMessageContaining("at least grantforge.audit.batch.size");
        for (String version : List.of("", " ", "v".repeat(65))) {
            assertThatThrownBy(() -> runtime.start(baseline, version)).hasMessageContaining("1 to 64 characters");
        }
        Map<String, String> unreadable = new HashMap<>(baseline);
        unreadable.put("grantforge.token.file", temporary.resolve("missing").toString());
        assertThatThrownBy(() -> runtime.start(unreadable, "version")).isInstanceOf(IOException.class);
        unreadable.put("grantforge.token.file", baseline.get("grantforge.token.file"));
        unreadable.put("grantforge.signing.key.file", temporary.resolve("missing-key").toString());
        assertThatThrownBy(() -> runtime.start(unreadable, "version")).isInstanceOf(IOException.class);
        verifyNoInteractions(factory, agent);
    }

    @Test
    void startsOnceStopsOnceAndRestarts() throws IOException
    {
        GrantForgeAgent restarted = mock(GrantForgeAgent.class);
        when(factory.apply(any(AgentSettings.class))).thenReturn(agent, restarted);
        runtime.stop();
        assertThatThrownBy(runtime::policies).isInstanceOf(IllegalStateException.class).hasMessageContaining("not running");
        assertThat(runtime.start(settings(), "version")).isTrue();
        assertThat(runtime.start(Map.of(), "")).as("already running").isFalse();
        verify(agent, never()).close();
        runtime.stop();
        runtime.stop();
        verify(agent).close();
        assertThat(runtime.start(settings(), "version-2")).isTrue();
        runtime.close();
        verify(restarted).close();
        verify(factory, times(2)).apply(any(AgentSettings.class));
    }

    @Test
    void capturesTheSnapshotOfTheHiveService() throws IOException
    {
        runtime.start(settings(), "version");
        assertThat(runtime.policies().enforced()).as("without a snapshot everything is refused").isTrue();
        Snapshot snapshot = new HiveSnapshots().disabled().snapshot();
        when(agent.snapshot()).thenReturn(snapshot);
        assertThat(runtime.policies().enforced()).isFalse();
        Snapshot other = mock(Snapshot.class);
        when(other.serviceType()).thenReturn("hdfs");
        when(agent.snapshot()).thenReturn(other);
        assertThatThrownBy(runtime::policies).isInstanceOf(IllegalStateException.class).hasMessageContaining("not hive");
    }

    @Test
    void recordsEventsWhileRunning() throws IOException
    {
        AccessEvent event = AccessEvent.builder("alice", "sales", "select", true).build();
        assertThat(runtime.record(event)).isFalse();
        runtime.start(settings(), "version");
        when(agent.record(event)).thenReturn(true);
        assertThat(runtime.record(event)).isTrue();
        verify(agent).record(event);
    }
}
