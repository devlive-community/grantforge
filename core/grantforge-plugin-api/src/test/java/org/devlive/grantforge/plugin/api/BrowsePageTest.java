// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrowsePageTest
{
    @Test
    void copiesItsEntriesAndSaysWhereTheNextPageStarts()
    {
        List<BrowseEntry> entries = new ArrayList<>(List.of(new BrowseEntry("a", "/a", true, null, null, null, null, null)));
        BrowsePage page = new BrowsePage("/", "/", entries, "a");
        entries.clear();

        assertThat(page.entries()).hasSize(1);
        assertThat(page.nextCursor()).isEqualTo("a");
        assertThatThrownBy(() -> page.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new BrowsePage("/data", "/data/x", List.of(), null).nextCursor()).isNull();
    }
}
