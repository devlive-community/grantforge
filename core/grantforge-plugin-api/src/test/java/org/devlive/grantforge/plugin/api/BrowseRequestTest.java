// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowseRequestTest
{
    private static final ServiceConfig CONFIG = new ServiceConfig("demo", Map.of());

    @Test
    void asksForABoundedPage()
    {
        assertThat(new BrowseRequest(CONFIG, "path", "/data", "b", 500).cursor()).isEqualTo("b");
        assertThat(new BrowseRequest(CONFIG, "path", "", null, 1).directory()).isEmpty();
        for (int size : new int[] {0, BrowseRequest.MAX_PAGE_SIZE + 1}) {
            assertThatThrownBy(() -> new BrowseRequest(CONFIG, "path", "", null, size)).hasMessage("page size must be between 1 and 500");
        }
    }
}
