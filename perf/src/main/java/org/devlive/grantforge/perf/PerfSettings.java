// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import java.nio.file.Path;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * What a benchmark run does, from {@code perf.*} system properties. The defaults are the target scale of the
 * performance audit: a million accounts, ten thousand roles and a hundred thousand resources on PostgreSQL.
 *
 * @param database the database: {@code h2} or {@code <engine>:<version>} as {@code TestDatabase} takes it
 * @param users how many accounts to seed
 * @param roles how many custom roles to seed
 * @param resources how many resources the seeded application has, at least 1000
 * @param samples how many measured runs per metric
 * @param warmup how many unmeasured runs per metric before measuring
 * @param jmh whether to run the JMH benchmark of grant derivation too
 * @param enforce whether exceeding a threshold fails the run
 * @param thresholds the thresholds file
 * @param report where to write the JSON report
 */
record PerfSettings(String database, int users, int roles, int resources, int samples, int warmup, boolean jmh, boolean enforce,
        Path thresholds, Path report)
{
    /** Checks the values. */
    PerfSettings
    {
        requireNonNull(database, "database");
        requireNonNull(thresholds, "thresholds");
        requireNonNull(report, "report");
        if (users < 1000 || roles < 100 || resources < 1000) {
            throw new IllegalArgumentException("seed at least 1000 users, 100 roles and 1000 resources");
        }
        if (samples < 1 || warmup < 0) {
            throw new IllegalArgumentException("measure at least one run, and warm up zero or more");
        }
    }

    /**
     * Reads the settings.
     *
     * @param properties {@code perf.database}, {@code perf.users}, {@code perf.roles}, {@code perf.resources},
     *        {@code perf.samples}, {@code perf.warmup}, {@code perf.jmh}, {@code perf.enforce}, {@code perf.thresholds}
     *        and {@code perf.report}; each has a default
     * @return the settings
     * @throws IllegalArgumentException for a value out of range or not a number
     */
    static PerfSettings from(Properties properties)
    {
        return new PerfSettings(properties.getProperty("perf.database", "postgres:17"), number(properties, "perf.users", 1_000_000),
                number(properties, "perf.roles", 10_000), number(properties, "perf.resources", 100_000), number(properties, "perf.samples", 1000),
                number(properties, "perf.warmup", 200), Boolean.parseBoolean(properties.getProperty("perf.jmh", "true")),
                Boolean.parseBoolean(properties.getProperty("perf.enforce", "true")),
                Path.of(properties.getProperty("perf.thresholds", "perf/thresholds.properties")),
                Path.of(properties.getProperty("perf.report", "perf/target/perf-report.json")));
    }

    private static int number(Properties properties, String key, int fallback)
    {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip().replace("_", ""));
        }
        catch (NumberFormatException notNumber) {
            throw new IllegalArgumentException(key + " is not a number: " + value, notNumber);
        }
    }
}
