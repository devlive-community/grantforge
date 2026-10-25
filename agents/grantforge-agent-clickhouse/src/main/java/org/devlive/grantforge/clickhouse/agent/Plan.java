// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * What the policies give one user in ClickHouse's terms: the privileges of the user's role, and the rows the user sees
 * of the tables it may read whose rows a policy filters.
 *
 * @param grants the privileges, sorted
 * @param filters the row filters, by table, sorted
 */
record Plan(List<Grant> grants, Map<TableRef, Filter> filters)
{
    /** A plan that gives nothing. */
    static final Plan NOTHING = new Plan(List.of(), Map.of());

    /** Sorts and copies the privileges and the filters. */
    Plan
    {
        grants = grants.stream().sorted(Grant.ORDER).toList();
        filters = sorted(filters);
    }

    private static SortedMap<TableRef, Filter> sorted(Map<TableRef, Filter> filters)
    {
        SortedMap<TableRef, Filter> sorted = new TreeMap<>(TableRef.ORDER);
        sorted.putAll(filters);
        return Collections.unmodifiableSortedMap(sorted);
    }

    /**
     * A row filter.
     *
     * @param expression the condition, as ClickHouse's row policies take it
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
        return grants.isEmpty() && filters.isEmpty();
    }

    /**
     * Returns the statement that makes a role hold exactly the plan's privileges.
     *
     * @param role the role, quoted
     * @return a GRANT that replaces the role's privileges, or a REVOKE of all of them when the plan has none
     */
    String grantStatement(String role)
    {
        if (grants.isEmpty()) {
            return "REVOKE ALL ON *.* FROM " + role;
        }
        return grants.stream().map(Grant::sql).collect(Collectors.joining(", ", "GRANT ", " TO " + role + " WITH REPLACE OPTION"));
    }
}
