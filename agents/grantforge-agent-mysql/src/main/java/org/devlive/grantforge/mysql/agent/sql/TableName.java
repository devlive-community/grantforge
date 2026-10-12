// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import static java.util.Objects.requireNonNull;

/**
 * A table as the policies name it: its database and its own name, unquoted and folded as the server folds them.
 *
 * @param database the database
 * @param table the table
 */
public record TableName(String database, String table)
{
    /** Checks the parts. */
    public TableName
    {
        requireNonNull(database, "database");
        requireNonNull(table, "table");
    }

    @Override
    public String toString()
    {
        return database + "." + table;
    }
}
