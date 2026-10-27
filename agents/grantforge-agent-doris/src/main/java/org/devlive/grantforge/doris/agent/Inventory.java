// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The catalogs, databases, tables and columns of a cluster and its workload groups, each level read when first asked
 * for, as an external catalog can hold many. Doris's own databases are not in it: policies do not decide them.
 * Thread-safe: the audit log's reader asks for columns too.
 */
final class Inventory
{
    private final List<String> catalogs;
    private final List<String> workloadGroups;
    private final Function<String, List<String>> databases;
    private final BiFunction<String, String, List<String>> tables;
    private final Function<TableRef, List<String>> columns;
    private final Map<String, List<String>> databasesRead = new ConcurrentHashMap<>();
    private final Map<String, List<String>> tablesRead = new ConcurrentHashMap<>();
    private final Map<TableRef, List<String>> columnsRead = new ConcurrentHashMap<>();

    /**
     * Holds the inventory.
     *
     * @param catalogs the catalogs, the internal one first
     * @param workloadGroups the workload groups
     * @param databases reads a catalog's databases
     * @param tables reads a database's tables, by catalog and database
     * @param columns reads a table's columns
     */
    Inventory(List<String> catalogs, List<String> workloadGroups, Function<String, List<String>> databases,
            BiFunction<String, String, List<String>> tables, Function<TableRef, List<String>> columns)
    {
        this.catalogs = List.copyOf(catalogs);
        this.workloadGroups = List.copyOf(workloadGroups);
        this.databases = databases;
        this.tables = tables;
        this.columns = columns;
    }

    /**
     * Returns the catalogs.
     *
     * @return their names
     */
    List<String> catalogs()
    {
        return catalogs;
    }

    /**
     * Returns the workload groups.
     *
     * @return their names
     */
    List<String> workloadGroups()
    {
        return workloadGroups;
    }

    /**
     * Returns a catalog's databases.
     *
     * @param catalog the catalog
     * @return their names
     */
    List<String> databases(String catalog)
    {
        return databasesRead.computeIfAbsent(catalog, name -> List.copyOf(databases.apply(name)));
    }

    /**
     * Returns a database's tables and views.
     *
     * @param catalog the catalog
     * @param database the database
     * @return their names
     */
    List<String> tables(String catalog, String database)
    {
        return tablesRead.computeIfAbsent(catalog + "\u0000" + database, ignored -> List.copyOf(tables.apply(catalog, database)));
    }

    /**
     * Returns a table's columns.
     *
     * @param table the table
     * @return their names in the table's order, none for a table the cluster does not have
     */
    List<String> columns(TableRef table)
    {
        return columnsRead.computeIfAbsent(table, name -> List.copyOf(columns.apply(name)));
    }
}
