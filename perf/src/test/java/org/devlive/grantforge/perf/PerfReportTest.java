// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class PerfReportTest
{
    @Test
    void writesJsonAndATable()
    {
        Properties properties = new Properties();
        properties.setProperty("perf.database", "h2");
        PerfReport report = new PerfReport(PerfSettings.from(properties));
        Latencies runs = new Latencies();
        runs.add(2_000_000);
        runs.add(4_000_000);
        report.add("api.users.page", runs);
        report.note("seed.seconds", 12.5);

        String json = report.json(List.of("api.users.page.p95 = \"slow\"\n"));

        assertThat(json).contains("\"database\": \"h2\"", "\"users\": 1000000", "\"seed.seconds\": 12.500",
                "\"api.users.page\": {\"count\": 2, \"mean\": 3.000, \"p50\": 2.000, \"p95\": 4.000, \"p99\": 4.000, \"max\": 4.000}",
                "\"violations\": [\"api.users.page.p95 = \\\"slow\\\"\\n\"]");
        assertThat(report.table()).contains("api.users.page", "4.000", "seed.seconds");
        assertThat(report.results()).containsKey("api.users.page");
    }
}
