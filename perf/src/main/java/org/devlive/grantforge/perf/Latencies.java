// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import java.util.Arrays;

/** The durations of repeated runs of one operation, with their percentiles. Not thread-safe. */
final class Latencies
{
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private long[] nanos = new long[64];
    private int size;

    /**
     * Records one run.
     *
     * @param duration how long it took, in nanoseconds
     */
    void add(long duration)
    {
        if (duration < 0) {
            throw new IllegalArgumentException("a duration cannot be negative");
        }
        if (size == nanos.length) {
            nanos = Arrays.copyOf(nanos, size * 2);
        }
        nanos[size] = duration;
        size++;
    }

    /**
     * Returns how many runs were recorded.
     *
     * @return the count
     */
    int count()
    {
        return size;
    }

    /**
     * Returns a percentile by the nearest-rank method: the smallest duration that at least that share of runs did not
     * exceed.
     *
     * @param percent the percentile, above 0 and at most 100
     * @return milliseconds
     * @throws IllegalStateException if nothing was recorded
     */
    double percentile(double percent)
    {
        if (percent <= 0 || percent > 100) {
            throw new IllegalArgumentException("percentile must be in (0, 100]: " + percent);
        }
        long[] sorted = sorted();
        int rank = (int) Math.ceil(percent / 100 * sorted.length);
        return sorted[Math.max(rank, 1) - 1] / NANOS_PER_MILLI;
    }

    /**
     * Returns the mean duration.
     *
     * @return milliseconds
     * @throws IllegalStateException if nothing was recorded
     */
    double mean()
    {
        return Arrays.stream(sorted()).average().orElse(0) / NANOS_PER_MILLI;
    }

    private long[] sorted()
    {
        if (size == 0) {
            throw new IllegalStateException("no runs were recorded");
        }
        long[] sorted = Arrays.copyOf(nanos, size);
        Arrays.sort(sorted);
        return sorted;
    }
}
