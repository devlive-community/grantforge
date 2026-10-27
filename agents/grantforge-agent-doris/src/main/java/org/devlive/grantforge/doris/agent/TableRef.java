// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import java.util.Comparator;

/**
 * A table of a database of a catalog.
 *
 * @param catalog the catalog
 * @param database the database
 * @param table the table
 */
record TableRef(String catalog, String database, String table)
{
    /** Tables by catalog, database, then by name. */
    static final Comparator<TableRef> ORDER = Comparator.comparing(TableRef::catalog).thenComparing(TableRef::database)
            .thenComparing(TableRef::table);

    /**
     * Returns the table as Doris statements name it.
     *
     * @return such as {@code `internal`.`sales`.`orders`}
     */
    String sql()
    {
        return Privilege.quote(catalog) + "." + Privilege.quote(database) + "." + Privilege.quote(table);
    }

    /**
     * Returns the table as Doris shows it and as access events name it.
     *
     * @return such as {@code internal.sales.orders}
     */
    @Override
    public String toString()
    {
        return catalog + "." + database + "." + table;
    }
}
