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
}
