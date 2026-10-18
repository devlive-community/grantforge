// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

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

class KafkaAgentRuntimeTest
{
    @TempDir
    Path temporary;

    private final GrantForgeAgent agent = mock(GrantForgeAgent.class);
    @SuppressWarnings("unchecked")
    private final Function<AgentSettings, GrantForgeAgent> factory = mock(Function.class);
    private final KafkaAgentRuntime runtime = new KafkaAgentRuntime(factory);

    @BeforeEach
    void usesOnlyTheInjectedAgent()
    {
        when(factory.apply(any(AgentSettings.class))).thenReturn(agent);
    }

    @AfterEach
    void releasesRuntime()
    {
        runtime.release();
        runtime.release();
    }

    static Map<String, String> settings(Path directory) throws IOException
    {
        Path token = directory.resolve("token");
        Files.writeString(token, " token\n", StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        values.put("grantforge.token.file", token.toString());
        values.put("grantforge.server.url", "https://grantforge.example.com/");
        values.put("grantforge.instance", "broker-1");
        values.put("grantforge.cache.dir", directory.resolve("cache").toString());
        return values;
    }

    @Test
    void readsRequiredSettingsAndDefaults() throws IOException
    {
        Map<String, String> values = settings(temporary);
        values.replaceAll((key, value) -> " " + value + " \n");
        assertThat(runtime.acquire(values, "2026.2.0")).isTrue();

        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory).apply(parsed.capture());
        AgentSettings actual = parsed.getValue();
        assertThat(actual.server()).isEqualTo(URI.create("https://grantforge.example.com/"));
        assertThat(actual.token()).isEqualTo("token");
        assertThat(actual.instance()).isEqualTo("broker-1");
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
        Map<String, String> values = settings(temporary);
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
        runtime.acquire(values, "version");

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
        Map<String, String> baseline = settings(temporary);
        for (String key : List.of("token.file", "server.url", "instance", "cache.dir")) {
            Map<String, String> missing = new HashMap<>(baseline);
            missing.put("grantforge." + key, " ");
            assertThatThrownBy(() -> runtime.acquire(missing, "version")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing Kafka agent setting grantforge." + key);
            missing.remove("grantforge." + key);
            assertThatThrownBy(() -> runtime.acquire(missing, "version")).isInstanceOf(IllegalArgumentException.class);
        }
        for (Map.Entry<String, String> invalid : Map.of("connect.timeout.ms", "0", "refresh.interval.ms", "999", "audit.batch.size", "x",
                "audit.queue.capacity", "1000001", "audit.spool.limit.bytes", "-1").entrySet()) {
            Map<String, String> values = new HashMap<>(baseline);
            values.put("grantforge." + invalid.getKey(), invalid.getValue());
            assertThatThrownBy(() -> runtime.acquire(values, "version")).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("grantforge." + invalid.getKey());
        }
        Map<String, String> small = new HashMap<>(baseline);
        small.put("grantforge.audit.batch.size", "17");
        small.put("grantforge.audit.queue.capacity", "16");
        assertThatThrownBy(() -> runtime.acquire(small, "version")).hasMessageContaining("at least grantforge.audit.batch.size");
        for (String version : List.of("", " ", "v".repeat(65))) {
            assertThatThrownBy(() -> runtime.acquire(baseline, version)).hasMessageContaining("1 to 64 characters");
        }
        Map<String, String> unreadable = new HashMap<>(baseline);
        unreadable.put("grantforge.token.file", temporary.resolve("missing").toString());
        assertThatThrownBy(() -> runtime.acquire(unreadable, "version")).isInstanceOf(IOException.class);
        unreadable.put("grantforge.token.file", baseline.get("grantforge.token.file"));
        unreadable.put("grantforge.signing.key.file", temporary.resolve("missing-key").toString());
        assertThatThrownBy(() -> runtime.acquire(unreadable, "version")).isInstanceOf(IOException.class);
        verifyNoInteractions(factory, agent);
    }

    @Test
    void sharesOneAgentAmongItsAuthorizersAndStopsWithTheLast() throws IOException
    {
        GrantForgeAgent restarted = mock(GrantForgeAgent.class);
        when(factory.apply(any(AgentSettings.class))).thenReturn(agent, restarted);
        runtime.release();
        assertThatThrownBy(runtime::snapshot).isInstanceOf(IllegalStateException.class).hasMessageContaining("not running");
        assertThat(runtime.acquire(settings(temporary), "version")).isTrue();
        assertThat(runtime.acquire(Map.of(), "")).as("the controller's authorizer shares the broker's agent").isFalse();
        runtime.release();
        verify(agent, never()).close();
        runtime.release();
        verify(agent).close();
        runtime.release();
        assertThat(runtime.acquire(settings(temporary), "version-2")).isTrue();
        runtime.release();
        verify(restarted).close();
        verify(factory, times(2)).apply(any(AgentSettings.class));
    }

    @Test
    void returnsTheAgentsSnapshot() throws IOException
    {
        runtime.acquire(settings(temporary), "version");
        assertThat(runtime.snapshot()).isNull();
        Snapshot snapshot = new KafkaSnapshots().snapshot();
        when(agent.snapshot()).thenReturn(snapshot);
        assertThat(runtime.snapshot()).isSameAs(snapshot);
    }

    @Test
    void recordsEventsWhileRunning() throws IOException
    {
        AccessEvent event = AccessEvent.builder("alice", "orders", "write", true).build();
        assertThat(runtime.record(event)).isFalse();
        runtime.acquire(settings(temporary), "version");
        when(agent.record(event)).thenReturn(true);
        assertThat(runtime.record(event)).isTrue();
        verify(agent).record(event);
    }
}
