// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RedisSettingsTest
{
    private static ServiceConfig config(String... values)
    {
        Map<String, String> map = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return new ServiceConfig("cache", map);
    }

    private static List<String> fields(String... values)
    {
        return RedisSettings.problems(config(values)).stream().map(ConfigProblem::field).toList();
    }

    @Test
    void readsNodesWithTheirDefaults()
    {
        RedisSettings settings = RedisSettings.of(config(RedisSettings.NODES, " Redis1 , redis2:6380, redis1:6379, [::1]:7000"));
        assertThat(settings.nodes()).containsExactly("redis1:6379", "redis2:6380", "[::1]:7000");
        assertThat(settings.user()).isNull();
        assertThat(settings.password()).isNull();
        assertThat(settings.tlsMode()).isEqualTo(RedisSettings.DISABLE);
        assertThat(settings.timeout()).isEqualTo(10_000);
        assertThat(settings.toString()).doesNotContain("password");
    }

    @Test
    void readsEverySetting()
    {
        RedisSettings settings = RedisSettings.of(config(RedisSettings.NODES, "redis1", RedisSettings.USER, "lookup", RedisSettings.PASSWORD, "secret",
                RedisSettings.TLS_MODE, "VERIFY-FULL", RedisSettings.CA_FILE, "/ca.pem", RedisSettings.CERTIFICATE_FILE, "/client.pem", RedisSettings.KEY_FILE, "/client-key.pem",
                RedisSettings.KEY_PASSWORD, "key-secret", RedisSettings.TIMEOUT, "7000"));
        assertThat(settings).isEqualTo(new RedisSettings(List.of("redis1:6379"), "lookup", "secret", RedisSettings.VERIFY_FULL, "/ca.pem", "/client.pem",
                "/client-key.pem", "key-secret", 7000));
        assertThat(settings.toString()).contains("lookup").doesNotContain("secret");
    }

    @Test
    void wantsNodes()
    {
        assertThat(fields()).containsExactly(RedisSettings.NODES);
        assertThat(RedisSettings.problems(config()).get(0).reason()).isEqualTo(ConfigProblem.Reason.REQUIRED);
        assertThat(fields(RedisSettings.NODES, "redis 1")).containsExactly(RedisSettings.NODES);
        assertThat(fields(RedisSettings.NODES, "redis1:70000")).containsExactly(RedisSettings.NODES);
        assertThat(RedisSettings.nodes("redis1,")).isEmpty();
    }

    @Test
    void checksTheTlsSettings()
    {
        assertThat(fields(RedisSettings.NODES, "redis1", RedisSettings.TLS_MODE, "required")).containsExactly(RedisSettings.TLS_MODE);
        assertThat(fields(RedisSettings.NODES, "redis1", RedisSettings.CA_FILE, "/ca.pem", RedisSettings.CERTIFICATE_FILE, "/c.pem", RedisSettings.KEY_FILE, "/k.pem"))
                .containsExactly(RedisSettings.CA_FILE, RedisSettings.CERTIFICATE_FILE, RedisSettings.KEY_FILE);
        assertThat(fields(RedisSettings.NODES, "redis1", RedisSettings.TLS_MODE, "verify-ca", RedisSettings.KEY_FILE, "/k.pem")).containsExactly(RedisSettings.CERTIFICATE_FILE);
        assertThat(fields(RedisSettings.NODES, "redis1", RedisSettings.TLS_MODE, "verify-ca", RedisSettings.CA_FILE, "/ca.pem")).isEmpty();
    }

    @Test
    void checksTheTimeout()
    {
        assertThat(RedisSettings.timeout(config(RedisSettings.TIMEOUT, "600000"))).isEqualTo(600_000);
        for (String wrong : List.of("0", "600001", "soon", "1234567")) {
            assertThatIllegalArgumentException().isThrownBy(() -> RedisSettings.timeout(config(RedisSettings.TIMEOUT, wrong)));
        }
        assertThat(fields(RedisSettings.NODES, "redis1", RedisSettings.TIMEOUT, "soon")).containsExactly(RedisSettings.TIMEOUT);
    }

    @Test
    void readingUnusableSettingsNamesTheFirstProblem()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> RedisSettings.of(config())).withMessageStartingWith(RedisSettings.NODES + ": ");
        assertThat(RedisSettings.of(config(RedisSettings.NODES, "redis1", RedisSettings.PASSWORD, "")).password()).isNull();
    }
}
