// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * One resource a statement uses and how: a database, a table or a column with the access type it needs.
 *
 * @param database the database
 * @param table the table, or {@code null} for the database itself
 * @param column the column, or {@code null} for the table itself
 * @param accessType the access type, such as {@code select}
 */
public record Access(String database, @Nullable String table, @Nullable String column, String accessType)
{
    /** Checks the parts. */
    public Access
    {
        requireNonNull(database, "database");
        requireNonNull(accessType, "accessType");
        if (table == null && column != null) {
            throw new IllegalArgumentException("a column needs its table");
        }
    }

    /**
     * Returns the level the access is checked at.
     *
     * @return {@code database}, {@code table} or {@code column}
     */
    public String level()
    {
        return column != null ? "column" : table != null ? "table" : "database";
    }

    /**
     * Returns the resource as MySQL writes it.
     *
     * @return such as {@code sales.orders.card}
     */
    public String resource()
    {
        return column != null ? database + "." + table + "." + column : table != null ? database + "." + table : database;
    }
}
