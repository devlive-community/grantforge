// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.page;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest
{
    @Test
    void appliesDefaultsForMissingValues()
    {
        assertThat(PageQuery.of(null, null)).isEqualTo(new PageQuery(1, PageQuery.DEFAULT_SIZE));
        assertThat(PageQuery.of(3, null)).isEqualTo(new PageQuery(3, 20));
        assertThat(PageQuery.of(null, 50)).isEqualTo(new PageQuery(1, 50));
    }

    @Test
    void acceptsBoundaries()
    {
        assertThat(new PageQuery(1, 1).size()).isEqualTo(1);
        assertThat(new PageQuery(1, PageQuery.MAX_SIZE).size()).isEqualTo(200);
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "-1, 20", "1, 0", "1, 201", "1, -5"})
    void rejectsOutOfRangeValues(int page, int size)
    {
        assertThatThrownBy(() -> new PageQuery(page, size))
                .isInstanceOfSatisfying(GrantForgeException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }

    @Test
    void offsetDoesNotOverflow()
    {
        assertThat(new PageQuery(3, 20).offset()).isEqualTo(40);
        assertThat(new PageQuery(Integer.MAX_VALUE, 200).offset()).isEqualTo((Integer.MAX_VALUE - 1L) * 200);
    }
}
