// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdOrderTest
{
    private record Row(long id, String name)
    {
    }

    @Test
    void ordersRowsLikeTheirIds()
    {
        List<Row> rows = List.of(new Row(2, "b"), new Row(1, "a"), new Row(3, "c"), new Row(2, "again"));

        assertThat(IdOrder.arrange(List.of(3L, 1L, 2L), rows, Row::id)).extracting(Row::name).containsExactly("c", "a", "b");
        assertThat(IdOrder.arrange(List.of(4L, 1L), Set.copyOf(rows.subList(0, 2)), Row::id)).extracting(Row::name)
                .containsExactly("a");
        assertThat(IdOrder.arrange(List.<Long>of(), rows, Row::id)).isEmpty();
    }
}
