// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantSummaryTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresEveryDescriptiveValue()
    {
        Instant now = Instant.EPOCH;

        assertThat(new TenantSummary(1, "acme", "Acme", TenantStatus.ACTIVE, false, 3, now).accounts()).isEqualTo(3);
        assertThatThrownBy(() -> new TenantSummary(1, null, "Acme", TenantStatus.ACTIVE, false, 0, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantSummary(1, "acme", null, TenantStatus.ACTIVE, false, 0, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantSummary(1, "acme", "Acme", null, false, 0, now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantSummary(1, "acme", "Acme", TenantStatus.ACTIVE, false, 0, null))
                .isInstanceOf(NullPointerException.class);
    }
}
