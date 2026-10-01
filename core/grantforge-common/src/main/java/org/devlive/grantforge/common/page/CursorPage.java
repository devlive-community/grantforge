// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.page;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One page of a keyset (cursor) paginated list.
 *
 * <p>Used for large or append-only tables such as audit events: the next page continues after the last
 * returned key, so the cost does not grow with the page number and concurrent inserts cannot shift rows
 * between pages. The cursor is opaque to clients.
 *
 * @param items the items of this page; copied into an immutable list
 * @param nextCursor cursor for the following page, or {@code null} when this is the last page
 * @param <T> item type
 */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor)
{
    /** Copies the items and normalises a blank cursor to {@code null}. */
    public CursorPage
    {
        items = List.copyOf(requireNonNull(items, "items"));
        nextCursor = Strings.blankToNull(nextCursor);
    }

    /**
     * Returns whether another page follows.
     *
     * @return {@code true} if {@link #nextCursor()} is present
     */
    public boolean hasNext()
    {
        return nextCursor != null;
    }
}
