// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsAgentRuntimeTest
{
    @TempDir
    Path temporary;

    private Map<String, String> settings() throws IOException
    {
        Path token = temporary.resolve("token");
        Files.writeString(token, " token\n");
        Map<String, String> values = new HashMap<>();
        values.put("grantforge.hdfs.token.file", token.toString());
        values.put("grantforge.hdfs.server.url", "https://grantforge.example.com/");
        values.put("grantforge.hdfs.instance", "nn-1");
        values.put("grantforge.hdfs.cache.dir", temporary.resolve("cache").toString());
        return values;
    }

    @Test
    void ownsTheAgentLifecycleAndReadsDefaultsWithoutHadoop() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        AtomicInteger starts = new AtomicInteger();
        HdfsAgentRuntime runtime = new HdfsAgentRuntime(parsed -> {
            starts.incrementAndGet();
            return agent;
        });
        assertThatThrownBy(runtime::settings).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(runtime::session).isInstanceOf(IllegalStateException.class);
        runtime.start(settings(), "2026.1.0-hadoop-2.7.7");
        runtime.start(Map.of(), "invalid");
        assertThat(starts).hasValue(1);
        AgentSettings parsed = runtime.settings();
        assertThat(parsed.token()).isEqualTo("token");
        assertThat(parsed.agentVersion()).isEqualTo("2026.1.0-hadoop-2.7.7");
        assertThat(parsed.auditQueueCapacity()).isEqualTo(10000);
        assertThat(parsed.auditBatchSize()).isEqualTo(500);
        assertThat(parsed.auditFlushInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(parsed.spoolLimitBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(runtime.nativeFallback()).isFalse();
        runtime.stop();
        runtime.close();
        verify(agent).close();
        assertThatThrownBy(() -> runtime.authorizer().authorize(HdfsAuthorizerTest.context(4).build()))
                .isInstanceOf(IOException.class);
        runtime.start(settings(), "2026.1.0-hadoop-2.7.7");
        runtime.stop();
        assertThat(starts).hasValue(2);
        verify(agent, times(2)).close();
    }

    @Test
    void readsAuditBoundsTimeoutsFallbackAndPinnedKey() throws Exception
    {
        Map<String, String> values = settings();
        values.put("grantforge.hdfs.native.fallback", "true");
        values.put("grantforge.hdfs.audit.batch.size", "17");
        values.put("grantforge.hdfs.audit.queue.capacity", "42");
        values.put("grantforge.hdfs.audit.flush.interval.ms", "200");
        values.put("grantforge.hdfs.audit.spool.limit.bytes", "0");
        values.put("grantforge.hdfs.connect.timeout.ms", "400");
        values.put("grantforge.hdfs.read.timeout.ms", "600");
        values.put("grantforge.hdfs.refresh.interval.ms", "2000");
        Path key = temporary.resolve("key");
        Files.writeString(key, Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
                .getPublic().getEncoded()));
        values.put("grantforge.hdfs.signing.key.file", key.toString());
        HdfsAgentRuntime runtime = new HdfsAgentRuntime(parsed -> mock(GrantForgeAgent.class));
        runtime.start(values, "2026.1.0-hadoop-3.5.0");
        AgentSettings parsed = runtime.settings();
        assertThat(parsed.auditBatchSize()).isEqualTo(17);
        assertThat(parsed.auditQueueCapacity()).isEqualTo(42);
        assertThat(parsed.auditFlushInterval()).isEqualTo(Duration.ofMillis(200));
        assertThat(parsed.spoolLimitBytes()).isZero();
        assertThat(parsed.connectTimeout()).isEqualTo(Duration.ofMillis(400));
        assertThat(parsed.readTimeout()).isEqualTo(Duration.ofMillis(600));
        assertThat(parsed.refreshInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(parsed.trustedKey()).isNotNull();
        assertThat(runtime.nativeFallback()).isTrue();
        runtime.close();
    }

    @Test
    void rejectsMissingInvalidAndUnsafeSettingsBeforeStarting() throws IOException
    {
        HdfsAgentRuntime runtime = new HdfsAgentRuntime(parsed -> mock(GrantForgeAgent.class));
        assertThatThrownBy(() -> runtime.start(Map.of(), "version")).isInstanceOf(IllegalArgumentException.class);
        Map<String, String> baseline = settings();
        Map<String, String> invalid = new HashMap<>(baseline);
        invalid.put("dfs.permissions.enabled", "false");
        assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IllegalArgumentException.class);
        invalid.clear();
        invalid.putAll(baseline);
        invalid.put("dfs.namenode.inode.attributes.provider.bypass.users", "alice");
        assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IllegalArgumentException.class);
        invalid.clear();
        invalid.putAll(baseline);
        invalid.put("grantforge.hdfs.native.fallback", "typo");
        assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IllegalArgumentException.class);
        for (String setting : new String[] {"connect.timeout.ms", "read.timeout.ms", "audit.flush.interval.ms"}) {
            invalid.clear();
            invalid.putAll(baseline);
            invalid.put("grantforge.hdfs." + setting, Long.toString(Integer.MAX_VALUE + 1L));
            assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IllegalArgumentException.class);
        }
        for (String setting : new String[] {"audit.batch.size", "audit.queue.capacity", "refresh.interval.ms"}) {
            invalid.clear();
            invalid.putAll(baseline);
            invalid.put("grantforge.hdfs." + setting, "0");
            assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IllegalArgumentException.class);
        }
        invalid.clear();
        invalid.putAll(baseline);
        invalid.put("grantforge.hdfs.audit.batch.size", "not-a-number");
        assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an integer");
        invalid.clear();
        invalid.putAll(baseline);
        invalid.put("grantforge.hdfs.audit.batch.size", "500");
        invalid.put("grantforge.hdfs.audit.queue.capacity", "1");
        assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IllegalArgumentException.class);
        invalid.clear();
        invalid.putAll(baseline);
        invalid.put("grantforge.hdfs.token.file", temporary.resolve("missing").toString());
        assertThatThrownBy(() -> runtime.start(invalid, "version")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> runtime.start(baseline, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runtime.start(baseline, "v".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        runtime.close();
    }

    @Test
    void capturesOneSnapshotAndReportsRuntimeGauges() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        Snapshot first = HdfsAuthorizerTest.snapshot(7, "hdfs", "/data/file", "read");
        Snapshot second = HdfsAuthorizerTest.snapshot(8, "hdfs", null, "read");
        when(agent.snapshot()).thenReturn(first, second);
        when(agent.queuedEvents()).thenReturn(23);
        when(agent.droppedEvents()).thenReturn(4L);
        when(agent.serverReachable()).thenReturn(true);
        HdfsAgentRuntime runtime = new HdfsAgentRuntime(parsed -> agent);
        runtime.start(settings(), "version");
        RecordingMetrics monitor = new RecordingMetrics();
        runtime.setMetrics(monitor);
        Function<AccessRequest, AgentDecision> captured = runtime.session();
        AccessRequest request = AccessRequest.builder("alice", "read").resource("path", "/data/file").build();
        assertThat(captured.apply(request).outcome()).isEqualTo(AgentDecision.Outcome.DENIED);
        assertThat(monitor.version).isEqualTo(7);
        assertThat(monitor.queued).isEqualTo(23);
        assertThat(monitor.dropped).isEqualTo(4);
        assertThat(monitor.reachable).isTrue();
        assertThat(runtime.session().apply(request).allowed()).isTrue();
        assertThat(captured.apply(request).outcome()).isEqualTo(AgentDecision.Outcome.DENIED);
        runtime.authorizer().nativeDenied(HdfsAuthorizerTest.context(4).build(), "native denied");
        verify(agent).record(any());
        runtime.close();
    }

    @Test
    void missingWrongTypeAndUnavailableMonitoringDoNotPermitIncorrectAccess() throws IOException
    {
        GrantForgeAgent agent = mock(GrantForgeAgent.class);
        HdfsAgentRuntime runtime = new HdfsAgentRuntime(parsed -> agent);
        runtime.start(settings(), "version");
        RecordingMetrics monitor = new RecordingMetrics();
        runtime.setMetrics(monitor);
        assertThat(runtime.session().apply(AccessRequest.builder("alice", "read").resource("path", "/data/file").build()).outcome())
                .isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(monitor.missing).isEqualTo(1);
        when(agent.snapshot()).thenReturn(HdfsAuthorizerTest.snapshot(7, "hive", null, "read"));
        assertThatThrownBy(runtime::session).isInstanceOf(IllegalStateException.class).hasMessageContaining("not hdfs");
        Snapshot correct = HdfsAuthorizerTest.snapshot(7, "hdfs", null, "read");
        when(agent.snapshot()).thenReturn(correct);
        runtime.setMetrics(new HdfsMetrics()
        {
            @Override
            public void snapshot(long version, long queued, long dropped, boolean reachable)
            {
                throw new IllegalStateException("monitor unavailable");
            }
        });
        assertThat(runtime.session().apply(AccessRequest.builder("alice", "read").resource("path", "/data/file").build()).allowed()).isTrue();
        runtime.setMetrics(null);
        assertThat(runtime.metrics()).isSameAs(HdfsMetrics.NONE);
        runtime.close();
        new HdfsAgentRuntime().close();
    }

    private static final class RecordingMetrics
            implements HdfsMetrics
    {
        long version;
        long queued;
        long dropped;
        boolean reachable;
        int missing;

        @Override
        public void missingSnapshot()
        {
            missing++;
        }

        @Override
        public void snapshot(long version, long queued, long dropped, boolean reachable)
        {
            this.version = version;
            this.queued = queued;
            this.dropped = dropped;
            this.reachable = reachable;
        }
    }
}
