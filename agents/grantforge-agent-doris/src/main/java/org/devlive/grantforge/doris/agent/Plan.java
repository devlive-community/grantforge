// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the policies give one user in Doris's terms: the privileges of the user's role on catalogs, databases, tables and
 * workload groups, the columns it may read of the tables it may read only in part, and the rows it sees of the tables
 * it may read whose rows a policy filters.
 *
 * @param privileges the privileges on objects as a whole, sorted
 * @param columns the columns the user may read, by table, for tables it may not read as a whole
 * @param filters the row filters, by table
 */
record Plan(List<Privilege> privileges, Map<TableRef, SortedSet<String>> columns, Map<TableRef, Filter> filters)
{
    /** A plan that gives nothing. */
    static final Plan NOTHING = new Plan(List.of(), Map.of(), Map.of());

    /** Sorts and copies everything. */
    Plan
    {
        privileges = privileges.stream().distinct().sorted(Privilege.ORDER).toList();
        SortedMap<TableRef, SortedSet<String>> sortedColumns = new TreeMap<>(TableRef.ORDER);
        columns.forEach((table, names) -> {
            if (!names.isEmpty()) {
                sortedColumns.put(table, Collections.unmodifiableSortedSet(new TreeSet<>(names)));
            }
        });
        columns = Collections.unmodifiableSortedMap(sortedColumns);
        SortedMap<TableRef, Filter> sortedFilters = new TreeMap<>(TableRef.ORDER);
        sortedFilters.putAll(filters);
        filters = Collections.unmodifiableSortedMap(sortedFilters);
    }

    /**
     * A row filter.
     *
     * @param expression the condition, as Doris's row policies take it
     * @param policyId the row filtering policy it comes from
     */
    record Filter(String expression, long policyId)
    {
    }

    /**
     * Returns whether the plan gives nothing, so that the user needs no role.
     *
     * @return {@code true} if it has neither privileges, columns nor filters
     */
    boolean empty()
    {
        return privileges.isEmpty() && columns.isEmpty() && filters.isEmpty();
    }

    /**
     * Returns the privileges as a set, to compare with what a role holds.
     *
     * @return them
     */
    Set<Privilege> privilegeSet()
    {
        return Set.copyOf(privileges);
    }
}
