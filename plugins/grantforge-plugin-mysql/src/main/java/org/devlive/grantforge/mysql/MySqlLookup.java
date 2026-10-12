// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The information_schema query that lists the databases, tables or columns starting with what was typed, within the
 * databases and tables a policy already names. Policy values are wildcards ({@code *} and {@code ?}); they become
 * {@code LIKE} patterns with {@code !} as the escape character, which holds whatever the server's SQL mode says about
 * backslashes.
 *
 * @param sql the statement
 * @param parameters its parameters in order
 */
record MySqlLookup(String sql, List<Object> parameters)
{
    /** The most values of a parent level a lookup is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_PARENTS = 100;

    /**
     * Builds the query for one level.
     *
     * @param resource the level: database, table or column
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most values wanted
     * @return the query
     * @throws IllegalArgumentException for a level the service type does not have
     */
    static MySqlLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        List<Object> parameters = new ArrayList<>();
        parameters.add(escape(typed.strip()) + "%");
        StringBuilder sql = new StringBuilder(256);
        String column;
        switch (resource) {
            case MySqlProvider.DATABASE -> {
                column = "SCHEMA_NAME";
                sql.append("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME LIKE ? ESCAPE '!'");
            }
            case MySqlProvider.TABLE -> {
                column = "TABLE_NAME";
                sql.append("SELECT DISTINCT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_NAME LIKE ? ESCAPE '!'");
                within(sql, parameters, "TABLE_SCHEMA", context.get(MySqlProvider.DATABASE));
            }
            case MySqlProvider.COLUMN -> {
                column = "COLUMN_NAME";
                sql.append("SELECT DISTINCT COLUMN_NAME FROM information_schema.COLUMNS WHERE COLUMN_NAME LIKE ? ESCAPE '!'");
                within(sql, parameters, "TABLE_SCHEMA", context.get(MySqlProvider.DATABASE));
                within(sql, parameters, "TABLE_NAME", context.get(MySqlProvider.TABLE));
            }
            default -> throw new IllegalArgumentException("MySQL services have no resource " + resource);
        }
        sql.append(" ORDER BY ").append(column).append(" LIMIT ?");
        parameters.add(limit);
        return new MySqlLookup(sql.toString(), List.copyOf(parameters));
    }

    /** Narrows the query to the values of a parent level, unless the policy names none, any or too many. */
    private static void within(StringBuilder sql, List<Object> parameters, String column, @Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_PARENTS || values.stream().anyMatch(value -> "*".equals(value.strip()))) {
            return;
        }
        sql.append(" AND (");
        for (int index = 0; index < values.size(); index++) {
            sql.append(index == 0 ? "" : " OR ").append(column).append(" LIKE ? ESCAPE '!'");
            parameters.add(like(values.get(index).strip()));
        }
        sql.append(')');
    }

    /**
     * Turns a policy's wildcard into a {@code LIKE} pattern.
     *
     * @param wildcard the value, with {@code *} for any run of characters and {@code ?} for one
     * @return the pattern
     */
    static String like(String wildcard)
    {
        return escape(wildcard).replace('*', '%').replace('?', '_');
    }

    /**
     * Escapes the characters {@code LIKE} reads specially.
     *
     * @param text plain text
     * @return the text matching only itself
     */
    static String escape(String text)
    {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * Runs the query.
     *
     * @param connection the connection
     * @return the values in the server's order
     * @throws SQLException when the server fails the query
     */
    List<String> run(Connection connection) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            List<String> values = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
            }
            return values;
        }
    }
}
