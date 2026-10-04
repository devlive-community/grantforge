// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.GrantForgeException.Reason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrantForgeExceptionTest
{
    @Test
    void carriesItsReasonAndStatus()
    {
        GrantForgeException refused = new GrantForgeException(Reason.FORBIDDEN, "no", null);

        assertThat(refused.getReason()).isEqualTo(Reason.FORBIDDEN);
        assertThat(refused).hasMessage("no");
        assertThat(Reason.UNAUTHENTICATED.status()).isEqualTo(401);
        assertThat(Reason.FORBIDDEN.status()).isEqualTo(403);
        assertThat(Reason.UNAVAILABLE.status()).isEqualTo(503);
    }
}
