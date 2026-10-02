// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PluginStateTest
{
    @Test
    void recordsAndChangesASwitch()
    {
        PluginState state = PluginState.of("hdfs", false, Instant.EPOCH);
        assertThat(state.getPluginId()).isEqualTo("hdfs");
        assertThat(state.isEnabled()).isFalse();
        state.set(true, Instant.EPOCH.plusSeconds(1));
        assertThat(state.isEnabled()).isTrue();
    }
}
