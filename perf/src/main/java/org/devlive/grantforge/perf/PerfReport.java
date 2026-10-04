// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/** The results of a run, as JSON for the CI artifact and as a table for the log. */
final class PerfReport
{
    private final PerfSettings settings;
    private final Map<String, Latencies> results = new LinkedHashMap<>();
    private final Map<String, Double> facts = new LinkedHashMap<>();

    /**
     * Starts a report.
     *
     * @param settings what the run does
     */
    PerfReport(PerfSettings settings)
    {
        this.settings = requireNonNull(settings, "settings");
    }

    /**
     * Adds the runs of a metric.
     *
     * @param metric its name, such as {@code api.users.page}
     * @param latencies the measured runs
     */
    void add(String metric, Latencies latencies)
    {
        results.put(requireNonNull(metric, "metric"), requireNonNull(latencies, "latencies"));
    }

    /**
     * Adds a single number, such as how long seeding took.
     *
     * @param name its name
     * @param value the number
     */
    void note(String name, double value)
    {
        facts.put(requireNonNull(name, "name"), value);
    }

    /**
     * Returns the measured metrics.
     *
     * @return the runs by metric, in the order they were measured
     */
    Map<String, Latencies> results()
    {
        return results;
    }

    /**
     * Writes the report as JSON.
     *
     * @param violations the thresholds exceeded
     * @return the JSON document
     */
    String json(List<String> violations)
    {
        String measured = results.entrySet().stream().map(metric -> {
            Latencies runs = metric.getValue();
            return "    " + quote(metric.getKey()) + String.format(Locale.ROOT,
                    ": {\"count\": %d, \"mean\": %s, \"p50\": %s, \"p95\": %s, \"p99\": %s, \"max\": %s}", runs.count(), number(runs.mean()),
                    number(runs.percentile(50)), number(runs.percentile(95)), number(runs.percentile(99)), number(runs.percentile(100)));
        }).collect(Collectors.joining(",\n"));
        return "{\n  \"database\": " + quote(settings.database()) + ",\n"
                + String.format(Locale.ROOT, "  \"scale\": {\"users\": %d, \"roles\": %d, \"resources\": %d, \"samples\": %d},%n",
                        settings.users(), settings.roles(), settings.resources(), settings.samples())
                + "  \"facts\": {" + facts.entrySet().stream().map(fact -> quote(fact.getKey()) + ": " + number(fact.getValue()))
                        .collect(Collectors.joining(", ")) + "},\n"
                + "  \"metrics\": {\n" + measured + "\n  },\n"
                + "  \"violations\": [" + violations.stream().map(PerfReport::quote).collect(Collectors.joining(", ")) + "]\n}\n";
    }

    /**
     * Writes the results as a table.
     *
     * @return one line per metric, milliseconds
     */
    String table()
    {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%-32s %8s %10s %10s %10s %10s%n", "metric", "count", "p50 ms",
                "p95 ms", "p99 ms", "max ms"));
        results.forEach((metric, runs) -> table.append(String.format(Locale.ROOT, "%-32s %8d %10.3f %10.3f %10.3f %10.3f%n", metric, runs.count(),
                runs.percentile(50), runs.percentile(95), runs.percentile(99), runs.percentile(100))));
        facts.forEach((name, value) -> table.append(String.format(Locale.ROOT, "%-32s %s%n", name, number(value))));
        return table.toString();
    }

    private static String number(double value)
    {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String quote(String text)
    {
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    }
                    else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
