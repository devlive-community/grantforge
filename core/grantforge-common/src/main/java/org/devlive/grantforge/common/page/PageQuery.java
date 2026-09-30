// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.page;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.jspecify.annotations.Nullable;

/**
 * A validated offset page request.
 *
 * <p>Pages are 1-based. The size is capped at {@link #MAX_SIZE} so that no request can load a whole
 * table; lists that need more (exports, audit history) use {@link CursorPage} or streaming instead.
 *
 * @param page 1-based page number
 * @param size number of items per page, between 1 and {@link #MAX_SIZE}
 */
public record PageQuery(int page, int size)
{
    /** Default number of items per page. */
    public static final int DEFAULT_SIZE = 20;
    /** Largest accepted page size. */
    public static final int MAX_SIZE = 200;

    /**
     * Validates the values.
     *
     * @throws GrantForgeException with {@link CommonErrorCode#BAD_REQUEST} if a value is out of range
     */
    public PageQuery
    {
        if (page < 1) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "page must be at least 1 but was " + page);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST,
                    "size must be between 1 and " + MAX_SIZE + " but was " + size);
        }
    }

    /**
     * Creates a query from optional request parameters, applying defaults for missing values.
     *
     * @param page 1-based page number; {@code null} means the first page
     * @param size page size; {@code null} means {@link #DEFAULT_SIZE}
     * @return the validated query
     * @throws GrantForgeException with {@link CommonErrorCode#BAD_REQUEST} if a value is out of range
     */
    public static PageQuery of(@Nullable Integer page, @Nullable Integer size)
    {
        return new PageQuery(page == null ? 1 : page, size == null ? DEFAULT_SIZE : size);
    }

    /**
     * Returns the number of items before this page.
     *
     * @return the zero-based offset; computed in {@code long} so large page numbers cannot overflow
     */
    public long offset()
    {
        return (long) (page - 1) * size;
    }
}
