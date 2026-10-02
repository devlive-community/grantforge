// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ValidityPeriodTest
{
    @Test
    void mayBeOpenAtEitherEnd()
    {
        Instant noon = Instant.parse("2026-06-01T12:00:00Z");
        assertThat(new ValidityPeriod(noon, null).from()).isEqualTo(noon);
        assertThat(new ValidityPeriod(null, noon).from()).isNull();
    }
}
