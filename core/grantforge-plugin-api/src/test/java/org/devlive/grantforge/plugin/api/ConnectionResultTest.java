// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionResultTest
{
    @Test
    void connectionResultsSayWhatHappened()
    {
        assertThat(ConnectionResult.succeeded().status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(ConnectionResult.failed("refused")).isEqualTo(new ConnectionResult(ConnectionResult.Status.FAILED,
                "refused"));
        assertThat(ConnectionResult.unsupported().message()).isNull();
    }
}
