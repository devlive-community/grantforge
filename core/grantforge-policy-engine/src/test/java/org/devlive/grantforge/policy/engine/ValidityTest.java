// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidityTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void startsInclusiveEndsExclusiveAndMayBeOpen()
    {
        Validity period = Validity.between(NOW, NOW.plusSeconds(10));
        assertThat(period.contains(NOW)).isTrue();
        assertThat(period.contains(NOW.plusSeconds(10))).isFalse();
        assertThat(period.contains(NOW.minusSeconds(1))).isFalse();
        assertThat(Validity.between(null, null).contains(NOW)).isTrue();
        assertThatThrownBy(() -> Validity.between(NOW, NOW)).hasMessageContaining("end after it starts");
    }
}
