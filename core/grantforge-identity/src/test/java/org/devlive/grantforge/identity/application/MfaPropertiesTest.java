// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaPropertiesTest
{
    @Test
    void acceptsWindowsFromAMinuteToTwelveHours()
    {
        MfaProperties properties = new MfaProperties(Duration.ofMinutes(10), true);

        assertThat(properties.stepUpWindow()).hasMinutes(10);
        assertThat(properties.requiredForSensitive()).isTrue();
        assertThat(new MfaProperties(Duration.ofHours(12), false).stepUpWindow()).hasHours(12);
        assertThatThrownBy(() -> new MfaProperties(Duration.ofSeconds(59), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaProperties(Duration.ofHours(13), false)).isInstanceOf(IllegalArgumentException.class);
    }
}
