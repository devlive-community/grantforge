// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.devlive.grantforge.plugin.api.BrowsePage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrowsePageResponseTest
{
    @Test
    void carriesEveryEntryWithItsMetadataAndTheNextCursor()
    {
        BrowsePage page = new BrowsePage("/data", "/data/sales", List.of(
                new BrowseEntry("2026", "/data/sales/2026", true, "etl", "analysts", "rwxr-x---", null, Instant.EPOCH),
                new BrowseEntry("readme.txt", "/data/sales/readme.txt", false, null, null, null, 42L, null)), "readme.txt");

        BrowsePageResponse response = BrowsePageResponse.from(page);
        assertThat(response.root()).isEqualTo("/data");
        assertThat(response.directory()).isEqualTo("/data/sales");
        assertThat(response.nextCursor()).isEqualTo("readme.txt");
        assertThat(response.entries()).containsExactly(
                new BrowseEntryResponse("2026", "/data/sales/2026", true, "etl", "analysts", "rwxr-x---", null, Instant.EPOCH),
                new BrowseEntryResponse("readme.txt", "/data/sales/readme.txt", false, null, null, null, 42L, null));
    }
}
