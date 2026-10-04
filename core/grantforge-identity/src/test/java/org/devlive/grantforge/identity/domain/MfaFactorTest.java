// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MfaFactorTest
{
    private static final Instant NOW = Instant.parse("2026-06-01T09:00:00Z");

    @Test
    void isConfirmedOnceAndUsesEachStepOnce()
    {
        MfaFactor factor = MfaFactor.enroll(7, "sealed");
        assertThat(factor.getAccountId()).isEqualTo(7);
        assertThat(factor.getSecret()).isEqualTo("sealed");
        assertThat(factor.isConfirmed()).isFalse();

        factor.confirm(NOW, 100);
        assertThat(factor.isConfirmed()).isTrue();
        assertThat(factor.getConfirmedAt()).isEqualTo(NOW);
        assertThat(factor.use(100)).isFalse();
        assertThat(factor.use(99)).isFalse();
        assertThat(factor.use(101)).isTrue();
        assertThat(factor.use(101)).isFalse();
    }

    @Test
    void enrollingAgainStartsOver()
    {
        MfaFactor factor = MfaFactor.enroll(7, "first");
        factor.confirm(NOW, 100);

        factor.reenroll("second");

        assertThat(factor.getSecret()).isEqualTo("second");
        assertThat(factor.isConfirmed()).isFalse();
        assertThat(factor.getConfirmedAt()).isNull();
        assertThat(factor.use(1)).isTrue();
    }
}
