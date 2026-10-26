// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import java.util.Comparator;

/**
 * A table, view or the like of a schema, within one database.
 *
 * @param schema the schema
 * @param table the table
 */
record TableRef(String schema, String table)
{
    /** Tables by schema, then by name. */
    static final Comparator<TableRef> ORDER = Comparator.comparing(TableRef::schema).thenComparing(TableRef::table);

    /**
     * Returns the table as PostgreSQL statements name it.
     *
     * @return such as {@code "crm"."orders"}
     */
    String sql()
    {
        return Sql.identifier(schema) + "." + Sql.identifier(table);
    }

    /**
     * Returns the table as messages name it.
     *
     * @return such as {@code crm.orders}
     */
    @Override
    public String toString()
    {
        return schema + "." + table;
    }
}
