// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** What the proxy knows of the server's schema, to tell which columns a statement uses and to mask them. */
public interface Catalog
{
    /**
     * Returns a table's columns.
     *
     * @param table the table, folded as {@link #foldsNames} says
     * @return its columns in their order, or {@code null} when the table does not exist or cannot be read
     * @throws IllegalStateException when the server cannot be asked
     */
    @Nullable List<String> columns(TableName table);

    /**
     * Returns whether a database has a stored function or procedure of a name.
     *
     * @param database the database
     * @param name the routine's name
     * @return {@code true} if it has
     * @throws IllegalStateException when the server cannot be asked
     */
    boolean routine(String database, String name);

    /**
     * Returns whether the server folds database and table names to lower case ({@code lower_case_table_names} 1 or 2).
     *
     * @return {@code true} if it does
     */
    boolean foldsNames();

    /** Forgets what it knows of tables, after a statement through the proxy changed some. */
    default void changed()
    {
        // A catalog that keeps nothing has nothing to forget.
    }
}
