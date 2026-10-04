// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatenciesTest
{
    @Test
    void takesPercentilesByNearestRank()
    {
        Latencies latencies = new Latencies();
        // 1..100 ms, recorded out of order and past the initial capacity.
        for (int millis = 100; millis >= 1; millis--) {
            latencies.add(millis * 1_000_000L);
        }

        assertThat(latencies.count()).isEqualTo(100);
        assertThat(latencies.percentile(50)).isEqualTo(50.0);
        assertThat(latencies.percentile(95)).isEqualTo(95.0);
        assertThat(latencies.percentile(99.5)).isEqualTo(100.0);
        assertThat(latencies.percentile(100)).isEqualTo(100.0);
        assertThat(latencies.percentile(0.1)).isEqualTo(1.0);
        assertThat(latencies.mean()).isEqualTo(50.5);
    }

    @Test
    void refusesWhatHasNoMeaning()
    {
        Latencies latencies = new Latencies();
        assertThatThrownBy(() -> latencies.percentile(50)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(latencies::mean).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> latencies.add(-1)).isInstanceOf(IllegalArgumentException.class);
        latencies.add(1);
        assertThatThrownBy(() -> latencies.percentile(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> latencies.percentile(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
