// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.page;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PageResultTest
{
    @Test
    void buildsFromQueryAndCopiesItems()
    {
        List<String> items = new ArrayList<>(List.of("a", "b"));
        PageResult<String> result = PageResult.of(new PageQuery(2, 2), items, 5);
        items.clear();

        assertThat(result.items()).containsExactly("a", "b");
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(3);
    }

    @Test
    void emptyListHasNoPages()
    {
        assertThat(PageResult.of(new PageQuery(1, 20), List.of(), 0).totalPages()).isZero();
        assertThat(PageResult.of(new PageQuery(1, 20), List.of(), 20).totalPages()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidNumbersAndNullElements()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), 0, 20, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), 1, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageResult<>(List.of(), 1, 20, -1));
        assertThatNullPointerException().isThrownBy(() -> new PageResult<>(Arrays.asList("a", null), 1, 20, 2));
    }
}
