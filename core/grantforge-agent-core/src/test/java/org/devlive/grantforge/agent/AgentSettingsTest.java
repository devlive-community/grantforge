// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgentSettingsTest
{
    private static AgentSettings.Builder valid()
    {
        return AgentSettings.builder().server(URI.create("https://grantforge.example.com")).token(" gfa_abc ").instance("namenode-1:8020")
                .cacheDirectory(Path.of("/var/lib/agent"));
    }

    @Test
    void defaultsSuitAnAgent()
    {
        AgentSettings settings = valid().build();

        assertThat(settings.server()).isEqualTo(URI.create("https://grantforge.example.com"));
        assertThat(settings.token()).isEqualTo("gfa_abc");
        assertThat(settings.instance()).isEqualTo("namenode-1:8020");
        assertThat(settings.host()).isNotBlank();
        assertThat(settings.agentVersion()).isEqualTo("unknown");
        assertThat(settings.cacheDirectory()).isEqualTo(Path.of("/var/lib/agent"));
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.refreshInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.auditBatchSize()).isEqualTo(500);
        assertThat(settings.auditFlushInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.auditQueueCapacity()).isEqualTo(10_000);
        assertThat(settings.spoolLimitBytes()).isEqualTo(64L * 1024 * 1024);
        assertThat(settings.trustedKey()).isNull();
    }

    @Test
    void takesWhatIsGiven()
    {
        SigningKey key = SigningKey.of(FakeServer.publicKey(FakeServer.keyPair()));
        AgentSettings settings = valid().host("nn1").agentVersion("2026.0.0").timeouts(Duration.ofSeconds(1), Duration.ofSeconds(2))
                .refreshInterval(Duration.ofSeconds(3)).audit(10, Duration.ofSeconds(4), 20).spoolLimitBytes(0).trustedKey(key).build();

        assertThat(settings.host()).isEqualTo("nn1");
        assertThat(settings.agentVersion()).isEqualTo("2026.0.0");
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.refreshInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.auditBatchSize()).isEqualTo(10);
        assertThat(settings.auditFlushInterval()).isEqualTo(Duration.ofSeconds(4));
        assertThat(settings.auditQueueCapacity()).isEqualTo(20);
        assertThat(settings.spoolLimitBytes()).isZero();
        assertThat(settings.trustedKey()).isSameAs(key);
    }

    @Test
    void refusesWhatCannotWork()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> AgentSettings.builder().build()).withMessageContaining("server");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().server(URI.create("ftp://host")).build()).withMessageContaining("server");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().server(URI.create("http:///path")).build()).withMessageContaining("server");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().token(" ").build()).withMessageContaining("token");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().instance("name node").build()).withMessageContaining("instance");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().instance("x".repeat(129)).build()).withMessageContaining("instance");
        assertThatIllegalArgumentException().isThrownBy(() -> AgentSettings.builder().server(URI.create("http://h")).token("t").instance("i").build())
                .withMessageContaining("cache directory");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().timeouts(Duration.ZERO, Duration.ofSeconds(1)).build())
                .withMessageContaining("connect timeout");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().timeouts(Duration.ofSeconds(1), Duration.ofSeconds(-1)).build())
                .withMessageContaining("read timeout");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().refreshInterval(Duration.ZERO).build()).withMessageContaining("refresh");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().audit(0, Duration.ofSeconds(1), 10).build()).withMessageContaining("batch");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().audit(1001, Duration.ofSeconds(1), 2000).build()).withMessageContaining("batch");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().audit(10, Duration.ofSeconds(1), 5).build()).withMessageContaining("queue");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().audit(10, Duration.ZERO, 50).build()).withMessageContaining("flush");
        assertThatIllegalArgumentException().isThrownBy(() -> valid().spoolLimitBytes(-1).build()).withMessageContaining("spool");
    }
}
