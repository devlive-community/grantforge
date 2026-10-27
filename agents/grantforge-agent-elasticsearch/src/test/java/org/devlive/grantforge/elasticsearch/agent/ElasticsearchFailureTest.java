// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchFailureTest
{
    @Test
    void tellsNodesThatCannotServeFromRefusals()
    {
        IOException cause = new IOException("refused");
        ElasticsearchFailure down = new ElasticsearchFailure("no node", 0, cause);
        assertThat(down.unreachable()).isTrue();
        assertThat(down.getCause()).isSameAs(cause);
        assertThat(down.status()).isZero();
        for (int status : new int[] {429, 502, 503, 504}) {
            assertThat(new ElasticsearchFailure("busy", status, null).unreachable()).isTrue();
        }
        for (int status : new int[] {400, 401, 403, 404, 500}) {
            assertThat(new ElasticsearchFailure("refused", status, null).unreachable()).isFalse();
        }
        assertThat(new ElasticsearchFailure("refused", 403, null).getMessage()).isEqualTo("refused");
    }
}
