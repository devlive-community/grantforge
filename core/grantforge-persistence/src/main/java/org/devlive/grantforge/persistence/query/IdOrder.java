// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.query;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Puts rows read by id back in the order of the ids, for lists that page through ids first (with filters and data scopes
 * as criteria) and then read the rows they show with a projection.
 */
public final class IdOrder
{
    private IdOrder()
    {
    }

    /**
     * Orders rows like their ids.
     *
     * @param ids the ids in the wanted order
     * @param rows the rows, in any order
     * @param id the id of a row
     * @param <I> the id type
     * @param <R> the row type
     * @return the rows in the order of the ids; ids without a row are skipped, and of rows sharing an id the first is kept
     */
    public static <I, R> List<R> arrange(List<I> ids, Collection<R> rows, Function<R, I> id)
    {
        Map<I, R> byId = requireNonNull(rows, "rows").stream()
                .collect(Collectors.toMap(requireNonNull(id, "id"), Function.identity(), (first, second) -> first));
        return requireNonNull(ids, "ids").stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
