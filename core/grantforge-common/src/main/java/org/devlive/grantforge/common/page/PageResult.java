// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.page;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One page of an offset-paginated list.
 *
 * @param items the items of this page; copied into an immutable list, {@code null} elements rejected
 * @param page 1-based page number
 * @param size requested page size
 * @param total total number of items across all pages; never negative
 * @param <T> item type
 */
public record PageResult<T>(List<T> items, int page, int size, long total)
{
    /**
     * Validates and copies the values.
     *
     * @throws IllegalArgumentException if {@code total} is negative or the numbers are out of range
     */
    public PageResult
    {
        items = List.copyOf(requireNonNull(items, "items"));
        if (page < 1 || size < 1 || total < 0) {
            throw new IllegalArgumentException(
                    "invalid page result: page=" + page + ", size=" + size + ", total=" + total);
        }
    }

    /**
     * Creates a result for a query.
     *
     * @param query the query that produced the items
     * @param items the items of the page
     * @param total total number of items
     * @param <T> item type
     * @return the result
     */
    public static <T> PageResult<T> of(PageQuery query, List<T> items, long total)
    {
        requireNonNull(query, "query");
        return new PageResult<>(items, query.page(), query.size(), total);
    }

    /**
     * Returns the number of pages.
     *
     * @return {@code ceil(total / size)}; {@code 0} for an empty list
     */
    public long totalPages()
    {
        return (total + size - 1) / size;
    }
}
