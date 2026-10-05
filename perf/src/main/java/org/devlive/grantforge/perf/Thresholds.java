// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Upper limits of benchmark results, as {@code <metric>.p<percentile>=<milliseconds>} (for example
 * {@code api.users.page.p95=200}) or {@code <metric>.mean=<milliseconds>}.
 */
final class Thresholds
{
    private static final Pattern KEY = Pattern.compile("(.+)\\.(p(\\d{1,2}(?:\\.\\d+)?|100)|mean)");

    private final Map<String, Double> limits;

    private Thresholds(Map<String, Double> limits)
    {
        this.limits = limits;
    }

    /**
     * Reads thresholds from a properties file.
     *
     * @param file the file
     * @return the thresholds
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException for a key or limit that cannot be understood
     */
    static Thresholds read(Path file) throws IOException
    {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return of(properties);
    }

    /**
     * Takes thresholds from properties.
     *
     * @param properties the limits by key
     * @return the thresholds
     * @throws IllegalArgumentException for a key or limit that cannot be understood
     */
    static Thresholds of(Properties properties)
    {
        Map<String, Double> limits = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("not <metric>.p<percentile> or <metric>.mean: " + key);
            }
            double limit;
            try {
                limit = Double.parseDouble(properties.getProperty(key).strip());
            }
            catch (NumberFormatException notNumber) {
                throw new IllegalArgumentException("the limit of " + key + " is not a number", notNumber);
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("the limit of " + key + " must be positive");
            }
            limits.put(key, limit);
        }
        return new Thresholds(limits);
    }

    /**
     * Compares results with the limits. A limit of a metric that was not measured is not checked, so a run can
     * measure a part of the benchmarks.
     *
     * @param results the measured metrics by name
     * @return one line per limit exceeded; empty if none is
     */
    List<String> violations(Map<String, Latencies> results)
    {
        requireNonNull(results, "results");
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, Double> limit : limits.entrySet()) {
            Matcher key = KEY.matcher(limit.getKey());
            if (!key.matches()) {
                continue;
            }
            Latencies measured = results.get(key.group(1));
            if (measured == null || measured.count() == 0) {
                continue;
            }
            String percentile = key.group(3);
            double value = percentile == null ? measured.mean() : measured.percentile(Double.parseDouble(percentile));
            if (value > limit.getValue()) {
                violations.add(String.format(Locale.ROOT, "%s = %.3f ms exceeds %.3f ms", limit.getKey(), value, limit.getValue()));
            }
        }
        return violations;
    }

    /**
     * Returns the limits.
     *
     * @return milliseconds by key
     */
    Map<String, Double> limits()
    {
        return Map.copyOf(limits);
    }
}
