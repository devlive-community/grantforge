// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
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

class ZooKeeperAgentRuntimeTest
{
    @TempDir
    Path temporary;

    private final GrantForgeAgent agent = mock(GrantForgeAgent.class);
    @SuppressWarnings("unchecked")
    private final Function<AgentSettings, GrantForgeAgent> factory = mock(Function.class);
    private final ZooKeeperAgentRuntime runtime = new ZooKeeperAgentRuntime(factory);

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
        values.put("grantforge.instance", "zk-1");
        values.put("grantforge.cache.dir", temporary.resolve("cache").toString());
        return values;
    }

    @Test
    void readsRequiredSettingsAndZooKeeperDefaults() throws IOException
    {
        Map<String, String> values = settings();
        values.replaceAll((key, value) -> " " + value + " \n");
        runtime.start(values, "2026.1.0-zookeeper");

        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory).apply(parsed.capture());
        AgentSettings actual = parsed.getValue();
        assertThat(actual.server()).isEqualTo(URI.create("https://grantforge.example.com/"));
        assertThat(actual.token()).isEqualTo("token");
        assertThat(actual.instance()).isEqualTo("zk-1");
        assertThat(actual.cacheDirectory()).isEqualTo(temporary.resolve("cache"));
        assertThat(actual.agentVersion()).isEqualTo("2026.1.0-zookeeper");
        assertThat(actual.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(actual.readTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(actual.refreshInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(actual.auditBatchSize()).isEqualTo(500);
        assertThat(actual.auditQueueCapacity()).isEqualTo(10000);
        assertThat(actual.auditFlushInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(actual.spoolLimitBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(actual.trustedKey()).isNull();
        assertThat(runtime.authorizer().user("alice/host@EXAMPLE.COM")).isEqualTo("alice");
        assertThat(runtime.authorizer().user("alice@EXAMPLE.COM")).isEqualTo("alice");
    }

    @Test
    void rejectsMissingAndBlankRequiredSettingsBeforeCreatingTheAgent() throws IOException
    {
        Map<String, String> baseline = settings();
        for (String key : new String[] {"token.file", "server.url", "instance", "cache.dir"}) {
            Map<String, String> missing = new HashMap<>(baseline);
            missing.remove("grantforge." + key);
            assertThatThrownBy(() -> runtime.start(missing, "version"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grantforge." + key);
            for (String blank : new String[] {"", " \t\n"}) {
                Map<String, String> invalid = new HashMap<>(baseline);
                invalid.put("grantforge." + key, blank);
                assertThatThrownBy(() -> runtime.start(invalid, "version"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grantforge." + key);
            }
        }
        verifyNoInteractions(factory, agent);
    }

    @Test
    void rejectsEmptyAndWhitespaceOnlyTokenFiles() throws IOException
    {
        Map<String, String> values = settings();
        for (String token : new String[] {"", " \t\r\n"}) {
            Files.writeString(temporary.resolve("token"), token, StandardCharsets.UTF_8);
            assertThatThrownBy(() -> runtime.start(values, "version"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token");
        }
        verifyNoInteractions(factory, agent);
    }

    @Test
    void propagatesUnreadableTokenAndSigningKeyFiles() throws IOException
    {
        Map<String, String> values = settings();
        values.put("grantforge.token.file", temporary.resolve("missing-token").toString());
        assertThatThrownBy(() -> runtime.start(values, "version")).isInstanceOf(IOException.class);
        values.put("grantforge.token.file", temporary.resolve("token").toString());
        values.put("grantforge.signing.key.file", temporary.resolve("missing-key").toString());
        assertThatThrownBy(() -> runtime.start(values, "version")).isInstanceOf(IOException.class);
        verifyNoInteractions(factory, agent);
    }

    @Test
    void rejectsNumericValuesOutsideTheirRangesBeforeCreatingTheAgent() throws IOException
    {
        Map<String, String> baseline = settings();
        Map<String, long[]> ranges = Map.of(
                "connect.timeout.ms", new long[] {1, Integer.MAX_VALUE},
                "read.timeout.ms", new long[] {1, Integer.MAX_VALUE},
                "refresh.interval.ms", new long[] {1000, Long.MAX_VALUE},
                "audit.batch.size", new long[] {1, AgentSettings.MAX_BATCH},
                "audit.queue.capacity", new long[] {1, 1_000_000},
                "audit.flush.interval.ms", new long[] {1, Integer.MAX_VALUE},
                "audit.spool.limit.bytes", new long[] {0, Long.MAX_VALUE});
        for (Map.Entry<String, long[]> entry : ranges.entrySet()) {
            String key = "grantforge." + entry.getKey();
            long[] bounds = entry.getValue();
            Map<String, String> invalid = new HashMap<>(baseline);
            invalid.put(key, Long.toString(bounds[0] - 1));
            assertThatThrownBy(() -> runtime.start(invalid, "version"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(key).hasMessageContaining("must be between");
            if (bounds[1] < Long.MAX_VALUE) {
                invalid.put(key, Long.toString(bounds[1] + 1));
                assertThatThrownBy(() -> runtime.start(invalid, "version"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(key).hasMessageContaining("must be between");
            }
        }
        verifyNoInteractions(factory, agent);
    }

    @Test
    void rejectsNonIntegerAndOverflowingNumericSettings() throws IOException
    {
        Map<String, String> baseline = settings();
        for (String key : new String[] {"connect.timeout.ms", "read.timeout.ms", "refresh.interval.ms", "audit.batch.size",
                "audit.queue.capacity", "audit.flush.interval.ms", "audit.spool.limit.bytes"}) {
            for (String value : new String[] {"", " ", "1.5", "not-a-number", "9223372036854775808", "-9223372036854775809"}) {
                Map<String, String> invalid = new HashMap<>(baseline);
                invalid.put("grantforge." + key, value);
                assertThatThrownBy(() -> runtime.start(invalid, "version"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grantforge." + key)
                        .hasMessageContaining("must be an integer").hasCauseInstanceOf(NumberFormatException.class);
            }
        }
        verifyNoInteractions(factory, agent);
    }

    @Test
    void rejectsQueuesSmallerThanOneBatch() throws IOException
    {
        Map<String, String> values = settings();
        values.put("grantforge.audit.batch.size", "17");
        values.put("grantforge.audit.queue.capacity", "16");
        assertThatThrownBy(() -> runtime.start(values, "version"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grantforge.audit.queue.capacity")
                .hasMessageContaining("grantforge.audit.batch.size");
        verifyNoInteractions(factory, agent);
    }

    @Test
    void acceptsNumericRangeEndpointsAndQueuesEqualToTheBatch() throws IOException
    {
        Map<String, String> values = settings();
        values.put("grantforge.connect.timeout.ms", "1");
        values.put("grantforge.read.timeout.ms", "1");
        values.put("grantforge.refresh.interval.ms", "1000");
        values.put("grantforge.audit.batch.size", "1");
        values.put("grantforge.audit.queue.capacity", "1");
        values.put("grantforge.audit.flush.interval.ms", "1");
        values.put("grantforge.audit.spool.limit.bytes", "0");
        runtime.start(values, "version");
        runtime.stop();
        values.put("grantforge.connect.timeout.ms", Integer.toString(Integer.MAX_VALUE));
        values.put("grantforge.read.timeout.ms", Integer.toString(Integer.MAX_VALUE));
        values.put("grantforge.refresh.interval.ms", Long.toString(Long.MAX_VALUE));
        values.put("grantforge.audit.batch.size", Integer.toString(AgentSettings.MAX_BATCH));
        values.put("grantforge.audit.queue.capacity", "1000000");
        values.put("grantforge.audit.flush.interval.ms", Integer.toString(Integer.MAX_VALUE));
        values.put("grantforge.audit.spool.limit.bytes", Long.toString(Long.MAX_VALUE));
        runtime.start(values, "version");

        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory, times(2)).apply(parsed.capture());
        AgentSettings minimum = parsed.getAllValues().get(0);
        assertThat(minimum.connectTimeout()).isEqualTo(Duration.ofMillis(1));
        assertThat(minimum.readTimeout()).isEqualTo(Duration.ofMillis(1));
        assertThat(minimum.refreshInterval()).isEqualTo(Duration.ofSeconds(1));
        assertThat(minimum.auditBatchSize()).isEqualTo(1);
        assertThat(minimum.auditQueueCapacity()).isEqualTo(1);
        assertThat(minimum.auditFlushInterval()).isEqualTo(Duration.ofMillis(1));
        assertThat(minimum.spoolLimitBytes()).isZero();
        AgentSettings maximum = parsed.getAllValues().get(1);
        assertThat(maximum.connectTimeout()).isEqualTo(Duration.ofMillis(Integer.MAX_VALUE));
        assertThat(maximum.readTimeout()).isEqualTo(Duration.ofMillis(Integer.MAX_VALUE));
        assertThat(maximum.refreshInterval()).isEqualTo(Duration.ofMillis(Long.MAX_VALUE));
        assertThat(maximum.auditBatchSize()).isEqualTo(AgentSettings.MAX_BATCH);
        assertThat(maximum.auditQueueCapacity()).isEqualTo(1_000_000);
        assertThat(maximum.auditFlushInterval()).isEqualTo(Duration.ofMillis(Integer.MAX_VALUE));
        assertThat(maximum.spoolLimitBytes()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void readsConfiguredNumbersAndPinnedSigningKey() throws Exception
    {
        Map<String, String> values = settings();
        values.put("grantforge.connect.timeout.ms", " 400 ");
        values.put("grantforge.read.timeout.ms", " 600 ");
        values.put("grantforge.refresh.interval.ms", " 2000 ");
        values.put("grantforge.audit.batch.size", " 17 ");
        values.put("grantforge.audit.queue.capacity", " 42 ");
        values.put("grantforge.audit.flush.interval.ms", " 200 ");
        values.put("grantforge.audit.spool.limit.bytes", " 8192 ");
        String encoded = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
                .getPublic().getEncoded());
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
        assertThat(actual.spoolLimitBytes()).isEqualTo(8192);
        assertThat(actual.trustedKey()).isNotNull();
        assertThat(actual.trustedKey().encoded()).isEqualTo(encoded);
    }

    @Test
    void ignoresBlankOptionalSigningKeyAndRejectsInvalidKeyContents() throws IOException
    {
        Map<String, String> values = settings();
        Path key = temporary.resolve("invalid-key");
        Files.writeString(key, "not-a-public-key", StandardCharsets.US_ASCII);
        values.put("grantforge.signing.key.file", key.toString());
        assertThatThrownBy(() -> runtime.start(values, "version"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("public key");
        verifyNoInteractions(factory, agent);

        values.put("grantforge.signing.key.file", " \t\n");
        runtime.start(values, "version");
        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory).apply(parsed.capture());
        assertThat(parsed.getValue().trustedKey()).isNull();
    }

    @Test
    void rejectsInvalidPrincipalMappingBeforeCreatingTheAgent() throws IOException
    {
        Map<String, String> baseline = settings();
        for (String mapping : new String[] {"", " ", "typo", "short,full"}) {
            Map<String, String> invalid = new HashMap<>(baseline);
            invalid.put("grantforge.principal.mapping", mapping);
            assertThatThrownBy(() -> runtime.start(invalid, "version"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("grantforge.principal.mapping")
                    .hasMessageContaining("short or full");
        }
        verifyNoInteractions(factory, agent);
    }

    @Test
    void appliesFullAndShortPrincipalMappingsWhenRestarted() throws IOException
    {
        Map<String, String> values = settings();
        values.put("grantforge.principal.mapping", " FULL ");
        runtime.start(values, "version");
        assertThat(runtime.authorizer().user("alice/host@EXAMPLE.COM")).isEqualTo("alice/host@EXAMPLE.COM");
        assertThat(runtime.authorizer().user("alice@EXAMPLE.COM")).isEqualTo("alice@EXAMPLE.COM");
        runtime.stop();
        values.put("grantforge.principal.mapping", " ShOrT ");
        runtime.start(values, "version");
        assertThat(runtime.authorizer().user("alice/host@EXAMPLE.COM")).isEqualTo("alice");
        assertThat(runtime.authorizer().user("alice@EXAMPLE.COM")).isEqualTo("alice");
        verify(factory, times(2)).apply(any(AgentSettings.class));
    }

    @Test
    void rejectsBlankAndOverlongVersionsAndAcceptsLengthEndpoints() throws IOException
    {
        Map<String, String> values = settings();
        for (String version : new String[] {"", " \t\n", "v".repeat(65)}) {
            assertThatThrownBy(() -> runtime.start(values, version))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 to 64 characters");
        }
        verifyNoInteractions(factory, agent);
        runtime.start(values, "v");
        runtime.stop();
        runtime.start(values, "v".repeat(64));
        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory, times(2)).apply(parsed.capture());
        assertThat(parsed.getAllValues()).extracting(AgentSettings::agentVersion).containsExactly("v", "v".repeat(64));
    }

    @Test
    void startsOnceClosesEachAgentOnceAndCanRestart() throws IOException
    {
        GrantForgeAgent restarted = mock(GrantForgeAgent.class);
        when(factory.apply(any(AgentSettings.class))).thenReturn(agent, restarted);
        runtime.stop();
        runtime.close();
        verifyNoInteractions(factory, agent, restarted);
        runtime.start(settings(), "version");
        runtime.start(Map.of(), "");
        verify(factory).apply(any(AgentSettings.class));
        verify(agent, never()).close();
        runtime.stop();
        runtime.stop();
        runtime.close();
        verify(agent).close();
        assertThatThrownBy(runtime::session).isInstanceOf(IllegalStateException.class).hasMessageContaining("not running");
        runtime.start(settings(), "version-2");
        runtime.session();
        verify(restarted).snapshot();
        verify(factory, times(2)).apply(any(AgentSettings.class));
        runtime.close();
        runtime.stop();
        verify(restarted).close();
        verify(agent).close();
        assertThatThrownBy(runtime::session).isInstanceOf(IllegalStateException.class).hasMessageContaining("not running");
    }

    @Test
    void refusesSessionsBeforeStart() throws IOException
    {
        assertThatThrownBy(runtime::session).isInstanceOf(IllegalStateException.class).hasMessageContaining("not running");
        verifyNoInteractions(factory, agent);
        runtime.start(settings(), "version");
        runtime.close();
        assertThatThrownBy(runtime::session).isInstanceOf(IllegalStateException.class).hasMessageContaining("not running");
        verify(agent, never()).snapshot();
    }

    @Test
    void returnsAnUndeterminedFunctionWhenThereIsNoSnapshot() throws IOException
    {
        runtime.start(settings(), "version");
        Function<AccessRequest, AgentDecision> captured = runtime.session();
        Snapshot later = mock(Snapshot.class);
        when(agent.snapshot()).thenReturn(later);
        AccessRequest request = AccessRequest.builder("alice", "read").resource("path", "/node").build();
        AgentDecision decision = captured.apply(request);
        assertThat(decision).isSameAs(AgentDecision.withoutSnapshot());
        assertThat(decision.outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.determined()).isFalse();
        assertThat(decision.policyId()).isNull();
        assertThat(decision.policyVersion()).isNull();
        assertThat(captured.apply(request)).isSameAs(decision);
        verify(agent).snapshot();
        verifyNoInteractions(later);
    }

    @Test
    void rejectsSnapshotsBoundToAnotherServiceType() throws IOException
    {
        Snapshot snapshot = mock(Snapshot.class);
        when(agent.snapshot()).thenReturn(snapshot);
        runtime.start(settings(), "version");
        for (String serviceType : new String[] {"hdfs", "ZooKeeper", ""}) {
            when(snapshot.serviceType()).thenReturn(serviceType);
            assertThatThrownBy(runtime::session).isInstanceOf(IllegalStateException.class).hasMessageContaining("not zookeeper");
        }
        verify(snapshot, never()).decide(any(AccessRequest.class));
    }

    @Test
    void capturesOneCorrectSnapshotAndDelegatesTheExactRequests() throws IOException
    {
        Snapshot first = mock(Snapshot.class);
        Snapshot second = mock(Snapshot.class);
        AgentDecision firstDecision = mock(AgentDecision.class);
        AgentDecision secondDecision = mock(AgentDecision.class);
        when(first.serviceType()).thenReturn("zookeeper");
        when(second.serviceType()).thenReturn("zookeeper");
        when(first.decide(any(AccessRequest.class))).thenReturn(firstDecision);
        when(second.decide(any(AccessRequest.class))).thenReturn(secondDecision);
        when(agent.snapshot()).thenReturn(first, second);
        runtime.start(settings(), "version");
        AccessRequest read = AccessRequest.builder("alice", "read").resource("path", "/node").build();
        AccessRequest write = AccessRequest.builder("bob", "write").resource("path", "/other").build();
        Function<AccessRequest, AgentDecision> captured = runtime.session();
        assertThat(captured.apply(read)).isSameAs(firstDecision);
        Function<AccessRequest, AgentDecision> current = runtime.session();
        assertThat(current.apply(write)).isSameAs(secondDecision);
        assertThat(captured.apply(write)).isSameAs(firstDecision);
        verify(agent, times(2)).snapshot();
        verify(first).decide(read);
        verify(first).decide(write);
        verify(second).decide(write);
        verify(second, never()).decide(read);
    }
}
