// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import com.clickhouse.client.api.sql.SQLUtils;

import java.util.Comparator;

/**
 * A table of a database.
 *
 * @param database the database
 * @param table the table
 */
record TableRef(String database, String table)
{
    /** Tables by database, then by name. */
    static final Comparator<TableRef> ORDER = Comparator.comparing(TableRef::database).thenComparing(TableRef::table);

    /**
     * Returns the table as ClickHouse statements name it.
     *
     * @return such as {@code "sales"."orders"}
     */
    String sql()
    {
        return SQLUtils.enquoteIdentifier(database) + "." + SQLUtils.enquoteIdentifier(table);
    }

    /**
     * Returns the table as access events name it.
     *
     * @return such as {@code sales.orders}
     */
    @Override
    public String toString()
    {
        return database + "." + table;
    }
}
