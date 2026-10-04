// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdsTest
{
    private static Latencies millis(long... values)
    {
        Latencies latencies = new Latencies();
        for (long value : values) {
            latencies.add(value * 1_000_000L);
        }
        return latencies;
    }

    private static Properties properties(String... pairs)
    {
        Properties properties = new Properties();
        for (int index = 0; index < pairs.length; index += 2) {
            properties.setProperty(pairs[index], pairs[index + 1]);
        }
        return properties;
    }

    @Test
    void reportsTheLimitsExceeded()
    {
        Thresholds thresholds = Thresholds.of(properties("api.users.page.p95", "200", "authz.snapshot.hit.p99", "1", "jmh.derivation.derive.mean",
                "5", "api.unmeasured.p50", "1"));

        assertThat(thresholds.violations(Map.of("api.users.page", millis(10, 20, 300), "authz.snapshot.hit", millis(1, 1),
                "jmh.derivation.derive", millis(7)))).containsExactly("api.users.page.p95 = 300.000 ms exceeds 200.000 ms",
                "jmh.derivation.derive.mean = 7.000 ms exceeds 5.000 ms");
        assertThat(thresholds.violations(Map.of("api.users.page", millis(10), "authz.snapshot.hit", new Latencies()))).isEmpty();
        assertThat(thresholds.limits()).containsEntry("authz.snapshot.hit.p99", 1.0).hasSize(4);
    }

    @Test
    void readsAFileAndRefusesWhatItCannotUnderstand(@TempDir Path folder) throws IOException
    {
        Path file = folder.resolve("thresholds.properties");
        Files.writeString(file, "# limits\napi.roles.list.p99.9 = 250\n");
        assertThat(Thresholds.read(file).limits()).containsExactly(Map.entry("api.roles.list.p99.9", 250.0));

        assertThatThrownBy(() -> Thresholds.of(properties("api.users.page", "200"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Thresholds.of(properties("api.users.page.p95", "fast"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Thresholds.of(properties("api.users.page.p95", "0"))).isInstanceOf(IllegalArgumentException.class);
    }
}
