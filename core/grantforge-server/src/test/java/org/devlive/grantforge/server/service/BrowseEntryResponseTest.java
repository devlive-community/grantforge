// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BrowseEntryResponseTest
{
    @Test
    void keepsEveryFieldOfTheEntry()
    {
        assertThat(BrowseEntryResponse.from(new BrowseEntry("2026", "/data/2026", true, "etl", "analysts", "rwxr-x---", null,
                Instant.EPOCH))).isEqualTo(new BrowseEntryResponse("2026", "/data/2026", true, "etl", "analysts", "rwxr-x---", null,
                Instant.EPOCH));
    }
}
