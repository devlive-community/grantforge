// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import org.devlive.grantforge.identity.application.TenantSummary;
import org.devlive.grantforge.identity.domain.TenantStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TenantResponseTest
{
    @Test
    void exposesTheIdAsAString()
    {
        Instant now = Instant.parse("2026-10-01T08:00:00Z");

        assertThat(TenantResponse.from(new TenantSummary(9_007_199_254_740_993L, "acme", "Acme", TenantStatus.ACTIVE,
                false, 2, now))).isEqualTo(new TenantResponse("9007199254740993", "acme", "Acme", TenantStatus.ACTIVE,
                false, 2, now));
    }
}
