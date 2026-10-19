// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Objects.requireNonNull;

/**
 * Who asks: the user of the query, the groups Trino's group provider gives the user, and the query, when there is one.
 *
 * @param user the session user
 * @param groups the user's groups, sorted and each once
 * @param query the query's id, or {@code null} for checks outside a query, such as impersonation
 */
public record Caller(String user, List<String> groups, @Nullable String query)
{
    /**
     * Creates a caller.
     *
     * @param user the session user
     * @param groups the user's groups, as Trino gives them
     * @param query the query's id, or {@code null}
     */
    public Caller(String user, Set<String> groups, @Nullable String query)
    {
        this(user, List.copyOf(groups), query);
    }

    /** Checks the parts and sorts the groups. */
    public Caller
    {
        requireNonNull(user, "user");
        groups = List.copyOf(new TreeSet<>(groups));
    }
}
