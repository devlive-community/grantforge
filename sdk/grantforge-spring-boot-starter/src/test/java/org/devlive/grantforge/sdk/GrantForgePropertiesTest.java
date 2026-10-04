// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantForgePropertiesTest
{
    @Test
    void checksTheValues()
    {
        assertThat(new GrantForgeProperties(URI.create("https://gf.example"), Duration.ZERO, 1, Duration.ofSeconds(1), null, null).cacheSize()).isOne();
        assertThatThrownBy(() -> new GrantForgeProperties(null, Duration.ofSeconds(-1), 1, Duration.ofSeconds(1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrantForgeProperties(null, Duration.ZERO, 0, Duration.ofSeconds(1), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GrantForgeProperties(null, Duration.ZERO, 1, Duration.ZERO, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
