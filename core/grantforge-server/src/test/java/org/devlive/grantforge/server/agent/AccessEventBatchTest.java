// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AccessEventBatchTest
{
    @SuppressWarnings("NullAway")
    @Test
    void leavesOutMissingEvents()
    {
        assertThat(new AccessEventBatch("a", null).events()).isEmpty();
        assertThat(new AccessEventBatch("a", Arrays.asList((AccessEventRequest) null)).events()).isEmpty();
    }
}
