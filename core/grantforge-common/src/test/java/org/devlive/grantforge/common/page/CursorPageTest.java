// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.page;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CursorPageTest
{
    @Test
    void pageWithCursorHasNext()
    {
        CursorPage<Integer> page = new CursorPage<>(List.of(1, 2), "abc");

        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("abc");
        assertThat(page.items()).containsExactly(1, 2);
    }

    @Test
    void missingOrBlankCursorMeansLastPage()
    {
        assertThat(new CursorPage<>(List.of(), null).hasNext()).isFalse();
        assertThat(new CursorPage<>(List.of(), "  ").nextCursor()).isNull();
    }
}
