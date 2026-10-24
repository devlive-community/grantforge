// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PulsarAgentRuntimeTest
{
    @TempDir
    Path temporary;

    private final GrantForgeAgent agent = mock(GrantForgeAgent.class);

    private AgentSettings started(Map<String, String> values, String version) throws IOException
    {
        @SuppressWarnings("unchecked")
        Function<AgentSettings, GrantForgeAgent> factory = mock(Function.class);
        when(factory.apply(any(AgentSettings.class))).thenReturn(agent);
        try (PulsarAgentRuntime runtime = PulsarAgentRuntime.start(values, version, factory, () -> 0L)) {
            assertThat(runtime.closed()).isFalse();
        }
        ArgumentCaptor<AgentSettings> parsed = ArgumentCaptor.forClass(AgentSettings.class);
        verify(factory).apply(parsed.capture());
        return parsed.getValue();
    }

    @Test
    void readsRequiredSettingsAndDefaults() throws IOException
    {
        Map<String, String> values = TestRuntime.settings(temporary);
        values.remove("grantforge.audit.fold.window.ms");
        values.replaceAll((key, value) -> " " + value + " \n");
        AgentSettings actual = started(values, " 2026.2.0 ");
        assertThat(actual.server()).isEqualTo(URI.create("https://grantforge.example.com/"));
        assertThat(actual.token()).isEqualTo("gfa_token");
        assertThat(actual.instance()).isEqualTo("broker-1");
        assertThat(actual.cacheDirectory()).isEqualTo(temporary.resolve("cache"));
        assertThat(actual.agentVersion()).isEqualTo("2026.2.0");
        assertThat(actual.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(actual.readTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(actual.refreshInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(actual.auditBatchSize()).isEqualTo(500);
        assertThat(actual.auditQueueCapacity()).isEqualTo(10_000);
        assertThat(actual.auditFlushInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(actual.spoolLimitBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(actual.trustedKey()).isNull();
    }

    @Test
    void readsConfiguredNumbersAndAPinnedSigningKey() throws Exception
    {
        Map<String, String> values = TestRuntime.settings(temporary);
        values.put("grantforge.connect.timeout.ms", " 400 ");
        values.put("grantforge.read.timeout.ms", "600");
        values.put("grantforge.refresh.interval.ms", "2000");
        values.put("grantforge.audit.batch.size", "17");
        values.put("grantforge.audit.queue.capacity", "42");
        values.put("grantforge.audit.flush.interval.ms", "200");
        values.put("grantforge.audit.spool.limit.bytes", "0");
        values.put("grantforge.decision.cache.size", "0");
        values.put("grantforge.decision.cache.ttl.ms", "5");
        String encoded = Base64.getEncoder().encodeToString(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        Path key = temporary.resolve("key");
        Files.writeString(key, " " + encoded + "\n", StandardCharsets.US_ASCII);
        values.put("grantforge.signing.key.file", " " + key + " ");
        AgentSettings actual = started(values, "version");
        assertThat(actual.connectTimeout()).isEqualTo(Duration.ofMillis(400));
        assertThat(actual.readTimeout()).isEqualTo(Duration.ofMillis(600));
        assertThat(actual.refreshInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(actual.auditBatchSize()).isEqualTo(17);
        assertThat(actual.auditQueueCapacity()).isEqualTo(42);
        assertThat(actual.auditFlushInterval()).isEqualTo(Duration.ofMillis(200));
        assertThat(actual.spoolLimitBytes()).isZero();
        assertThat(actual.trustedKey()).isNotNull();
        values.put("grantforge.signing.key.file", " ");
        assertThat(started(values, "version").trustedKey()).isNull();
    }

    @Test
    void refusesMissingAndWrongSettings() throws IOException
    {
        Map<String, String> values = TestRuntime.settings(temporary);
        values.remove("grantforge.instance");
        assertThatIllegalArgumentException().isThrownBy(() -> PulsarAgentRuntime.start(values, "v", settings -> agent, () -> 0L))
                .withMessage("missing Pulsar agent setting grantforge.instance");
        values.put("grantforge.instance", " ");
        assertThatIllegalArgumentException().isThrownBy(() -> PulsarAgentRuntime.start(values, "v", settings -> agent, () -> 0L))
                .withMessage("missing Pulsar agent setting grantforge.instance");
        values.put("grantforge.instance", "broker");
        for (Map.Entry<String, String> wrong : Map.of("grantforge.decision.cache.size", "-1", "grantforge.decision.cache.ttl.ms", "0",
                "grantforge.audit.fold.window.ms", "3600001", "grantforge.refresh.interval.ms", "999", "grantforge.audit.batch.size", "many").entrySet()) {
            Map<String, String> copy = new java.util.HashMap<>(values);
            copy.put(wrong.getKey(), wrong.getValue());
            assertThatIllegalArgumentException().as(wrong.getKey()).isThrownBy(() -> PulsarAgentRuntime.start(copy, "v", settings -> agent, () -> 0L))
                    .withMessageStartingWith(wrong.getKey());
        }
        values.put("grantforge.audit.batch.size", "50");
        values.put("grantforge.audit.queue.capacity", "10");
        assertThatIllegalArgumentException().isThrownBy(() -> PulsarAgentRuntime.start(values, "v", settings -> agent, () -> 0L))
                .withMessageContaining("at least");
        values.remove("grantforge.audit.queue.capacity");
        assertThatIllegalArgumentException().isThrownBy(() -> PulsarAgentRuntime.start(values, " ", settings -> agent, () -> 0L))
                .withMessageContaining("version");
        assertThatIllegalArgumentException().isThrownBy(() -> PulsarAgentRuntime.start(values, "v".repeat(65), settings -> agent, () -> 0L));
        values.put("grantforge.token.file", temporary.resolve("missing").toString());
        assertThatThrownBy(() -> PulsarAgentRuntime.start(values, "v", settings -> agent, () -> 0L)).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void refusesEverythingForATokenOfAnotherServiceType()
    {
        TestRuntime test = new TestRuntime(new PulsarSnapshots().serviceType("hive").snapshot());
        Policies policies = test.runtime().policies();
        assertThat(policies.received()).isFalse();
        assertThat(test.runtime().policies().received()).as("warned once, refused still").isFalse();
        test.snapshot(new PulsarSnapshots().serviceType("hive").version(2).snapshot());
        assertThat(test.runtime().policies().received()).isFalse();
        test.snapshot(new PulsarSnapshots().snapshot());
        assertThat(test.runtime().policies().received()).isTrue();
    }

    @Test
    void sendsEventsUntilClosedAndClosesOnce()
    {
        TestRuntime test = new TestRuntime(new PulsarSnapshots().snapshot());
        AccessEvent event = AccessEvent.builder("alice", "persistent://sales/orders/payments", "produce", true).build();
        test.runtime().audit(event);
        assertThat(test.events()).containsExactly(event);
        test.runtime().close();
        test.runtime().close();
        assertThat(test.runtime().closed()).isTrue();
        test.runtime().audit(AccessEvent.builder("alice", "persistent://sales/orders/payments", "produce", false).build());
        assertThat(test.events()).hasSize(1);
        assertThat(test.runtime().policies().received()).as("a closed agent refuses").isFalse();
        verify(test.agent(), times(1)).close();
        verify(test.agent(), never()).snapshot();
    }

    @Test
    void startsTheRealAgent() throws IOException
    {
        Map<String, String> values = TestRuntime.settings(temporary);
        values.put("grantforge.server.url", "http://127.0.0.1:1/");
        values.put("grantforge.connect.timeout.ms", "100");
        try (PulsarAgentRuntime runtime = PulsarAgentRuntime.start(values, "test")) {
            assertThat(runtime.policies().received()).isFalse();
            assertThat(List.of(runtime.closed())).containsExactly(false);
        }
    }
}
