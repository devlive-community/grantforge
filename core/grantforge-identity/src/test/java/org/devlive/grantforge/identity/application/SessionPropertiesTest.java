// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPropertiesTest
{
    @Test
    void defaultsToNoLimitAndOneMinutePrecision()
    {
        assertThat(SessionProperties.defaults()).isEqualTo(new SessionProperties(0, Duration.ofMinutes(1)));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void rejectsOutOfRangeValues()
    {
        assertThat(new SessionProperties(100, Duration.ofSeconds(10)).maxPerAccount()).isEqualTo(100);
        assertThat(new SessionProperties(1, Duration.ofHours(1)).activityInterval()).isEqualTo(Duration.ofHours(1));
        assertThatThrownBy(() -> new SessionProperties(-1, Duration.ofMinutes(1))).hasMessageContaining("max-per-account");
        assertThatThrownBy(() -> new SessionProperties(101, Duration.ofMinutes(1))).hasMessageContaining("max-per-account");
        assertThatThrownBy(() -> new SessionProperties(0, Duration.ofSeconds(9))).hasMessageContaining("activity-interval");
        assertThatThrownBy(() -> new SessionProperties(0, Duration.ofMinutes(61))).hasMessageContaining("activity-interval");
        assertThatThrownBy(() -> new SessionProperties(0, null)).isInstanceOf(NullPointerException.class);
    }
}
