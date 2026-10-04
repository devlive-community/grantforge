// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerfSettingsTest
{
    @Test
    void defaultsToTheTargetScale()
    {
        PerfSettings settings = PerfSettings.from(new Properties());

        assertThat(settings).extracting(PerfSettings::database, PerfSettings::users, PerfSettings::roles, PerfSettings::resources,
                PerfSettings::samples, PerfSettings::warmup, PerfSettings::jmh, PerfSettings::enforce)
                .containsExactly("postgres:17", 1_000_000, 10_000, 100_000, 1000, 200, true, true);
        assertThat(settings.thresholds()).isEqualTo(Path.of("perf/thresholds.properties"));
    }

    @Test
    void takesSystemProperties()
    {
        Properties properties = new Properties();
        properties.setProperty("perf.database", "h2");
        properties.setProperty("perf.users", "20_000");
        properties.setProperty("perf.roles", " 500 ");
        properties.setProperty("perf.resources", "10000");
        properties.setProperty("perf.samples", "100");
        properties.setProperty("perf.warmup", "0");
        properties.setProperty("perf.jmh", "false");
        properties.setProperty("perf.enforce", "false");
        properties.setProperty("perf.report", "/tmp/report.json");

        PerfSettings settings = PerfSettings.from(properties);

        assertThat(settings).extracting(PerfSettings::database, PerfSettings::users, PerfSettings::roles, PerfSettings::warmup, PerfSettings::jmh,
                PerfSettings::enforce).containsExactly("h2", 20_000, 500, 0, false, false);
        assertThat(settings.report()).isEqualTo(Path.of("/tmp/report.json"));
    }

    @Test
    void refusesTooSmallOrBrokenValues()
    {
        Properties small = new Properties();
        small.setProperty("perf.users", "999");
        assertThatThrownBy(() -> PerfSettings.from(small)).isInstanceOf(IllegalArgumentException.class);
        Properties broken = new Properties();
        broken.setProperty("perf.samples", "many");
        assertThatThrownBy(() -> PerfSettings.from(broken)).isInstanceOf(IllegalArgumentException.class);
        Properties none = new Properties();
        none.setProperty("perf.samples", "0");
        assertThatThrownBy(() -> PerfSettings.from(none)).isInstanceOf(IllegalArgumentException.class);
    }
}
