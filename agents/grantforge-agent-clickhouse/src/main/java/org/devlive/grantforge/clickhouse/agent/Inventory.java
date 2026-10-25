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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The databases and tables of a server, and the columns of a database's tables, read when first asked for. ClickHouse's
 * own databases are not in it: policies do not decide them. Thread-safe: the query log's reader asks for columns too.
 */
final class Inventory
{
    private final SortedMap<String, List<String>> tables;
    private final Function<String, Map<String, List<String>>> columns;
    private final Map<String, Map<String, List<String>>> read = new ConcurrentHashMap<>();

    /**
     * Holds the inventory.
     *
     * @param tables the tables of each database, a database without tables included
     * @param columns reads the columns of a database's tables, by table
     */
    Inventory(Map<String, List<String>> tables, Function<String, Map<String, List<String>>> columns)
    {
        SortedMap<String, List<String>> copy = new TreeMap<>();
        tables.forEach((database, names) -> copy.put(database, List.copyOf(names)));
        this.tables = Collections.unmodifiableSortedMap(copy);
        this.columns = columns;
    }

    /**
     * Returns the databases.
     *
     * @return their names, sorted
     */
    Iterable<String> databases()
    {
        return tables.keySet();
    }

    /**
     * Returns a database's tables.
     *
     * @param database the database
     * @return their names, none for a database the inventory does not have
     */
    List<String> tables(String database)
    {
        List<String> names = tables.get(database);
        return names == null ? List.of() : names;
    }

    /**
     * Returns a table's columns.
     *
     * @param table the table
     * @return their names in the table's order, none for a table the inventory does not have
     */
    List<String> columns(TableRef table)
    {
        List<String> names = read.computeIfAbsent(table.database(), columns).get(table.table());
        return names == null ? List.of() : names;
    }
}
