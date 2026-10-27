// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A Doris user the agent keeps a role for: one user name, which policies name, with each of its identities, as Doris
 * keeps a user per name and host.
 *
 * @param name the user name
 * @param identities the user's identities as Doris writes them, such as {@code 'alice'@'%'}
 * @param groups the roles the user holds, but the agent's own; policies name them as groups
 * @param granted the roles each identity holds, the agent's own included, by identity
 * @param columns the columns each identity may read through any grant, by table, as {@code SHOW ALL GRANTS} shows them
 */
record Account(String name, List<String> identities, Set<String> groups, Map<String, Set<String>> granted,
        Map<String, Map<TableRef, Set<String>>> columns)
{
    /** Copies everything. */
    Account
    {
        identities = List.copyOf(identities);
        groups = Set.copyOf(groups);
        granted = Map.copyOf(granted);
        columns = Map.copyOf(columns);
    }

    /**
     * Returns the columns of a table every identity of the user may read.
     *
     * @param table the table
     * @return the columns in lower case, as Doris compares column names without case; none when one identity may read none
     */
    Set<String> readableByAll(TableRef table)
    {
        if (identities.isEmpty()) {
            return Set.of();
        }
        Set<String> common = lower(columns.getOrDefault(identities.get(0), Map.of()).getOrDefault(table, Set.of()));
        identities.forEach(identity -> common.retainAll(lower(columns.getOrDefault(identity, Map.of()).getOrDefault(table, Set.of()))));
        return Set.copyOf(common);
    }

    /**
     * Returns the columns of a table some identity of the user may read.
     *
     * @param table the table
     * @return the columns in lower case
     */
    Set<String> readableByAny(TableRef table)
    {
        Set<String> any = new HashSet<>();
        for (String identity : identities) {
            any.addAll(lower(columns.getOrDefault(identity, Map.of()).getOrDefault(table, Set.of())));
        }
        return Set.copyOf(any);
    }

    /**
     * Returns the columns of a table some identity of the user may read, named as Doris shows them.
     *
     * @param table the table
     * @return the columns
     */
    Set<String> readableAsShown(TableRef table)
    {
        Set<String> any = new HashSet<>();
        for (String identity : identities) {
            any.addAll(columns.getOrDefault(identity, Map.of()).getOrDefault(table, Set.of()));
        }
        return Set.copyOf(any);
    }

    /**
     * Returns the tables some identity of the user may read some columns of.
     *
     * @return the tables
     */
    Set<TableRef> columnTables()
    {
        Set<TableRef> tables = new HashSet<>();
        columns.values().forEach(byTable -> tables.addAll(byTable.keySet()));
        return tables;
    }

    private static Set<String> lower(Set<String> names)
    {
        Set<String> lower = new HashSet<>();
        names.forEach(name -> lower.add(name.toLowerCase(Locale.ROOT)));
        return lower;
    }
}
