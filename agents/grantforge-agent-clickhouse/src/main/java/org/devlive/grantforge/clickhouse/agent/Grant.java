// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import com.clickhouse.client.api.sql.SQLUtils;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * What the agent grants for one access type: on the server, a database as a whole, a table, or some columns of a table.
 *
 * @param accessType the access type
 * @param database the database, or {@code null} for the server
 * @param table the table, or {@code null} for every table of the database
 * @param columns the columns, or none for every column
 */
record Grant(String accessType, @Nullable String database, @Nullable String table, List<String> columns)
{
    private static final Comparator<@Nullable String> NULLS_FIRST = Comparator.nullsFirst(Comparator.<String>naturalOrder());

    /** The server's grants first, then by database, the database as a whole before its tables, access type and columns. */
    static final Comparator<Grant> ORDER = Grant::compare;

    /** Copies the columns. */
    Grant
    {
        columns = List.copyOf(columns);
    }

    /** A grant on the server. */
    static Grant server(String accessType)
    {
        return new Grant(accessType, null, null, List.of());
    }

    /** A grant on every table of a database. */
    static Grant database(String accessType, String database)
    {
        return new Grant(accessType, database, null, List.of());
    }

    /** A grant on a table, or on some of its columns. */
    static Grant table(String accessType, TableRef table, List<String> columns)
    {
        return new Grant(accessType, table.database(), table.table(), columns);
    }

    /**
     * Returns the privileges as a GRANT statement lists them.
     *
     * @return such as {@code SELECT("id", "region") ON "sales"."orders"}
     */
    String sql()
    {
        String target = database == null ? "*.*" : SQLUtils.enquoteIdentifier(database) + "."
                + (table == null ? "*" : SQLUtils.enquoteIdentifier(table));
        String list = columns.isEmpty() ? "" : columns.stream().map(SQLUtils::enquoteIdentifier).collect(Collectors.joining(", ", "(", ")"));
        return Privileges.keywords(accessType).stream().map(keyword -> keyword + list).collect(Collectors.joining(", ")) + " ON " + target;
    }

    private static int compare(Grant one, Grant other)
    {
        int order = Objects.compare(one.database, other.database, NULLS_FIRST);
        if (order == 0) {
            order = Objects.compare(one.table, other.table, NULLS_FIRST);
        }
        if (order == 0) {
            order = one.accessType.compareTo(other.accessType);
        }
        return order != 0 ? order : String.join(",", one.columns).compareTo(String.join(",", other.columns));
    }
}
