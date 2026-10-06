// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsAgentSettingsTest
{
    @TempDir
    Path temporary;

    static Configuration configuration(Path temporary) throws IOException
    {
        Path token = temporary.resolve("token");
        Files.writeString(token, " secret-agent-token\n");
        Configuration configuration = new Configuration(false);
        configuration.set("grantforge.hdfs.server.url", "https://grantforge.example.com/");
        configuration.set("grantforge.hdfs.token.file", token.toString());
        configuration.set("grantforge.hdfs.instance", "namenode-1:8020");
        configuration.set("grantforge.hdfs.cache.dir", temporary.resolve("cache").toString());
        return configuration;
    }

    @Test
    void readsRequiredSettingsAndDeniesUndeterminedAccessByDefault() throws IOException
    {
        HdfsAgentSettings settings = HdfsAgentSettings.read(configuration(temporary));

        assertThat(settings.nativeFallback()).isFalse();
        assertThat(settings.agent().token()).isEqualTo("secret-agent-token");
        assertThat(settings.agent().instance()).isEqualTo("namenode-1:8020");
        assertThat(settings.agent().server().getScheme()).isEqualTo("https");
        assertThat(settings.agent().cacheDirectory()).isEqualTo(temporary.resolve("cache"));
        assertThat(settings.agent().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.agent().readTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(settings.agent().refreshInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.agent().auditBatchSize()).isEqualTo(500);
        assertThat(settings.agent().auditQueueCapacity()).isEqualTo(10000);
        assertThat(settings.agent().auditFlushInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.agent().spoolLimitBytes()).isEqualTo(64L * 1024 * 1024);
    }

    @Test
    void readsExplicitFallbackTimeoutsAndPinnedSigningKey() throws IOException, NoSuchAlgorithmException
    {
        Configuration configuration = configuration(temporary);
        configuration.set("grantforge.hdfs.native.fallback", "true");
        configuration.setLong("grantforge.hdfs.connect.timeout.ms", 400);
        configuration.setLong("grantforge.hdfs.read.timeout.ms", 600);
        configuration.setLong("grantforge.hdfs.refresh.interval.ms", 2000);
        Path keyFile = temporary.resolve("signing-key");
        byte[] encoded = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(encoded));
        configuration.set("grantforge.hdfs.signing.key.file", keyFile.toString());

        HdfsAgentSettings settings = HdfsAgentSettings.read(configuration);

        assertThat(settings.nativeFallback()).isTrue();
        assertThat(settings.agent().connectTimeout()).isEqualTo(Duration.ofMillis(400));
        assertThat(settings.agent().readTimeout()).isEqualTo(Duration.ofMillis(600));
        assertThat(settings.agent().refreshInterval()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.agent().trustedKey()).isNotNull();
    }

    @Test
    void rejectsMissingSettingsUnreadableOrEmptyTokenAndInvalidValues() throws IOException
    {
        assertThatThrownBy(() -> HdfsAgentSettings.read(new Configuration(false))).isInstanceOf(IllegalArgumentException.class);
        Configuration configuration = configuration(temporary);
        configuration.set("grantforge.hdfs.token.file", temporary.resolve("missing").toString());
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IOException.class);
        configuration.set("grantforge.hdfs.token.file", temporary.resolve("token").toString());
        Files.writeString(temporary.resolve("token"), "\n");
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class);
        Files.writeString(temporary.resolve("token"), "token");
        configuration.set("grantforge.hdfs.native.fallback", "typo");
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class);
        configuration.set("grantforge.hdfs.native.fallback", "false");
        configuration.setLong("grantforge.hdfs.refresh.interval.ms", 999);
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class);
        configuration.setLong("grantforge.hdfs.refresh.interval.ms", 1000);
        configuration.setLong("grantforge.hdfs.connect.timeout.ms", 0);
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class);
        configuration.setLong("grantforge.hdfs.connect.timeout.ms", 1000);
        configuration.set("grantforge.hdfs.server.url", "file:/tmp/server");
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDisablingNativePermissionsOrBypassingTheAgentAndBoundsNetworkTimeouts() throws IOException
    {
        Configuration configuration = configuration(temporary);
        configuration.setBoolean("dfs.permissions.enabled", false);
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dfs.permissions.enabled");
        configuration.setBoolean("dfs.permissions.enabled", true);
        configuration.set("dfs.namenode.inode.attributes.provider.bypass.users", "hdfs,alice");
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bypass.users");
        configuration.unset("dfs.namenode.inode.attributes.provider.bypass.users");
        for (String key : new String[] {"grantforge.hdfs.connect.timeout.ms", "grantforge.hdfs.read.timeout.ms"}) {
            configuration.setLong(key, Integer.MAX_VALUE);
            assertThat(HdfsAgentSettings.read(configuration).agent()).isNotNull();
            configuration.setLong(key, Integer.MAX_VALUE + 1L);
            assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class);
            configuration.unset(key);
        }
    }

    @Test
    void configuresAuditBatchingBufferingLatencyAndOfflineDiskUse() throws IOException
    {
        Configuration configuration = configuration(temporary);
        configuration.setInt("grantforge.hdfs.audit.batch.size", 200);
        configuration.setInt("grantforge.hdfs.audit.queue.capacity", 20000);
        configuration.setLong("grantforge.hdfs.audit.flush.interval.ms", 250);
        configuration.setLong("grantforge.hdfs.audit.spool.limit.bytes", 128L * 1024 * 1024);

        HdfsAgentSettings settings = HdfsAgentSettings.read(configuration);

        assertThat(settings.agent().auditBatchSize()).isEqualTo(200);
        assertThat(settings.agent().auditQueueCapacity()).isEqualTo(20000);
        assertThat(settings.agent().auditFlushInterval()).isEqualTo(Duration.ofMillis(250));
        assertThat(settings.agent().spoolLimitBytes()).isEqualTo(128L * 1024 * 1024);
    }

    @Test
    void acceptsAuditLimitsAndCanExplicitlyDisableTheDiskSpool() throws IOException
    {
        Configuration configuration = configuration(temporary);
        configuration.setInt("grantforge.hdfs.audit.batch.size", 1);
        configuration.setInt("grantforge.hdfs.audit.queue.capacity", 1);
        configuration.setLong("grantforge.hdfs.audit.flush.interval.ms", 1);
        configuration.setLong("grantforge.hdfs.audit.spool.limit.bytes", 0);
        HdfsAgentSettings minimal = HdfsAgentSettings.read(configuration);
        assertThat(minimal.agent().auditBatchSize()).isEqualTo(1);
        assertThat(minimal.agent().auditQueueCapacity()).isEqualTo(1);
        assertThat(minimal.agent().auditFlushInterval()).isEqualTo(Duration.ofMillis(1));
        assertThat(minimal.agent().spoolLimitBytes()).isZero();

        configuration.setInt("grantforge.hdfs.audit.batch.size", 1000);
        configuration.setInt("grantforge.hdfs.audit.queue.capacity", 1_000_000);
        configuration.setLong("grantforge.hdfs.audit.flush.interval.ms", Integer.MAX_VALUE);
        configuration.setLong("grantforge.hdfs.audit.spool.limit.bytes", Long.MAX_VALUE);
        HdfsAgentSettings maximal = HdfsAgentSettings.read(configuration);
        assertThat(maximal.agent().auditBatchSize()).isEqualTo(1000);
        assertThat(maximal.agent().auditQueueCapacity()).isEqualTo(1_000_000);
        assertThat(maximal.agent().auditFlushInterval()).isEqualTo(Duration.ofMillis(Integer.MAX_VALUE));
        assertThat(maximal.agent().spoolLimitBytes()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsUnboundedAuditSettingsAndQueuesSmallerThanABatch() throws IOException
    {
        Configuration configuration = configuration(temporary);
        Map<String, long[]> invalid = Map.of(
                "audit.batch.size", new long[] {0, -1, 1001, Long.MAX_VALUE},
                "audit.queue.capacity", new long[] {0, -1, 1_000_001, Long.MAX_VALUE},
                "audit.flush.interval.ms", new long[] {0, -1, Integer.MAX_VALUE + 1L},
                "audit.spool.limit.bytes", new long[] {-1});
        for (Map.Entry<String, long[]> entry : invalid.entrySet()) {
            String name = HdfsAgentSettings.PREFIX + entry.getKey();
            for (long value : entry.getValue()) {
                configuration.setLong(name, value);
                assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).as(name + "=" + value)
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name);
            }
            configuration.unset(name);
        }
        configuration.setInt("grantforge.hdfs.audit.batch.size", 500);
        configuration.setInt("grantforge.hdfs.audit.queue.capacity", 499);
        assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audit.queue.capacity").hasMessageContaining("audit.batch.size");
    }

    @Test
    void identifiesTheAuditPropertyWhenItsNumberIsMalformed() throws IOException
    {
        Configuration configuration = configuration(temporary);
        for (String key : new String[] {"audit.batch.size", "audit.queue.capacity", "audit.flush.interval.ms", "audit.spool.limit.bytes"}) {
            String name = HdfsAgentSettings.PREFIX + key;
            configuration.set(name, "not-an-integer");
            assertThatThrownBy(() -> HdfsAgentSettings.read(configuration)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(name).hasMessageContaining("integer").hasCauseInstanceOf(NumberFormatException.class);
            configuration.unset(name);
        }
    }
}
