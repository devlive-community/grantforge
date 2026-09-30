// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.page;

import org.junit.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PageModelTest
{
    @Test
    public void firstApiPageMapsToFirstRepositoryPage()
    {
        Pageable pageable = PageModel.getPageable(1, 20);
        PageModel<String> page = new PageModel<>(Collections.singletonList("item"), pageable, 1);

        assertEquals(0, pageable.getPageNumber());
        assertEquals(1, page.getNumber());
        assertEquals("item", page.getContent().get(0));
    }

    @Test
    public void paginationPreservesOffsetAndSort()
    {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        Pageable pageable = PageModel.getPageableAndSort(3, 20, sort);

        assertEquals(40L, pageable.getOffset());
        assertEquals(sort, pageable.getSort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroApiPageIsRejected()
    {
        PageModel.getPageable(0, 20);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroPageSizeIsRejected()
    {
        PageModel.getPageable(1, 0);
    }
}
