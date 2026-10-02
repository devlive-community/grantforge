// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestedTest
{
    @Test
    void countsWhatBecameOfABatch()
    {
        assertThat(new Ingested(1, 2, 3)).extracting(Ingested::accepted, Ingested::duplicates, Ingested::expired).containsExactly(1, 2, 3);
    }
}
