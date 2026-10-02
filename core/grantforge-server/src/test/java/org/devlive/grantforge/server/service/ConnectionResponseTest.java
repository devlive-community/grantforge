// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionResponseTest
{
    @Test
    void tellsHowATestWent()
    {
        assertThat(ConnectionResponse.from(ConnectionResult.failed("refused")))
                .isEqualTo(new ConnectionResponse(ConnectionResult.Status.FAILED, "refused"));
        assertThat(ConnectionResponse.from(ConnectionResult.succeeded()).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
    }
}
