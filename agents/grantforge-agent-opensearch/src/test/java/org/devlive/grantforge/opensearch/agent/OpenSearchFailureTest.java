// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchFailureTest
{
    @Test
    void tellsNodesThatCannotServeFromRefusals()
    {
        IOException cause = new IOException("refused");
        OpenSearchFailure down = new OpenSearchFailure("no node", 0, cause);
        assertThat(down.unreachable()).isTrue();
        assertThat(down.getCause()).isSameAs(cause);
        assertThat(down.status()).isZero();
        for (int status : new int[] {429, 502, 503, 504}) {
            assertThat(new OpenSearchFailure("busy", status, null).unreachable()).isTrue();
        }
        for (int status : new int[] {400, 401, 403, 404, 500}) {
            assertThat(new OpenSearchFailure("refused", status, null).unreachable()).isFalse();
        }
        assertThat(new OpenSearchFailure("refused", 403, null).getMessage()).isEqualTo("refused");
    }
}
