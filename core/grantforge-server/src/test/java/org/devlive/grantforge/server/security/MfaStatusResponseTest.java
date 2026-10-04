// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.MfaStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MfaStatusResponseTest
{
    @Test
    void copiesTheStatus()
    {
        assertThat(MfaStatusResponse.from(new MfaStatus(true, 3))).isEqualTo(new MfaStatusResponse(true, 3));
    }
}
