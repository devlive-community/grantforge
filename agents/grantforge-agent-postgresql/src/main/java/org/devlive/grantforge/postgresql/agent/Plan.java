// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What the policies give one user in PostgreSQL's terms, database by database: the privileges of the user's role, the
 * rows the user sees of the tables it may read whose rows a policy filters, and what could not be given as the policies
 * say.
 *
 * @param privileges the privileges, by database
 * @param filters the row filters, by database and table
 * @param notes why something the policies give was not given, such as a row filter of a view
 */
record Plan(Map<String, Set<Privilege>> privileges, Map<String, Map<TableRef, Filter>> filters, List<String> notes)
{
    /** A plan that gives nothing. */
    static final Plan NOTHING = new Plan(Map.of(), Map.of(), List.of());

    /** Copies everything, the filters sorted by table. */
    Plan
    {
        Map<String, Set<Privilege>> granted = new HashMap<>();
        privileges.forEach((database, those) -> {
            if (!those.isEmpty()) {
                granted.put(database, Set.copyOf(those));
            }
        });
        privileges = Map.copyOf(granted);
        Map<String, Map<TableRef, Filter>> filtered = new HashMap<>();
        filters.forEach((database, those) -> {
            if (!those.isEmpty()) {
                SortedMap<TableRef, Filter> sorted = new TreeMap<>(TableRef.ORDER);
                sorted.putAll(those);
                filtered.put(database, Collections.unmodifiableSortedMap(sorted));
            }
        });
        filters = Map.copyOf(filtered);
        notes = List.copyOf(notes);
    }

    /**
     * A row filter.
     *
     * @param expression the condition, as PostgreSQL's row security policies take it
     * @param policyId the row filtering policy it comes from
     */
    record Filter(String expression, long policyId)
    {
    }

    /**
     * Returns whether the plan gives nothing, so that the user needs no role.
     *
     * @return {@code true} if it has neither privileges nor filters
     */
    boolean empty()
    {
        return privileges.isEmpty() && filters.isEmpty();
    }

    /**
     * Returns the privileges in a database.
     *
     * @param database the database
     * @return them, none for a database the plan gives nothing in
     */
    Set<Privilege> privileges(String database)
    {
        return privileges.getOrDefault(database, Set.of());
    }

    /**
     * Returns the row filters in a database.
     *
     * @param database the database
     * @return them by table, none for a database the plan filters nothing in
     */
    Map<TableRef, Filter> filters(String database)
    {
        return filters.getOrDefault(database, Map.of());
    }
}
