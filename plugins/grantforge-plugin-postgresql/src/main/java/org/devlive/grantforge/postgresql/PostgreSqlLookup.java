// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A lookup of the databases, schemas, tables or columns whose names start with what was typed, within the databases,
 * schemas and tables a policy already names. Databases are listed from the database the service signs in to; the names
 * below them are each database's own, so the lookup signs in to each database the policy names, up to
 * {@value #MAX_DATABASES} of them, and skips those the lookup user may not connect to. PostgreSQL's catalogs show every
 * name to every user that may connect. Policy values are wildcards ({@code *} and {@code ?}); they become {@code LIKE}
 * patterns. PostgreSQL's own schemas are left out: policies do not decide them.
 *
 * @param resource the level: database, schema, table or column
 * @param typed what the user typed
 * @param databases the patterns of the databases to read, or none for every database
 * @param schemas the patterns of the schemas, or none for every schema
 * @param tables the patterns of the tables, or none for every table
 * @param limit the most values wanted
 */
record PostgreSqlLookup(String resource, String typed, List<String> databases, List<String> schemas, List<String> tables, int limit)
{
    /** The most values of a parent level a lookup is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_PARENTS = 100;
    /** The most databases a lookup below the databases signs in to. */
    static final int MAX_DATABASES = 20;

    /** The kinds of relations policies decide: tables, partitioned tables, views, materialized views and foreign tables. */
    static final String RELATION_KINDS = "('r', 'p', 'v', 'm', 'f')";
    /** Leaves out PostgreSQL's own schemas: those starting with pg_, which no user schema may, and information_schema. */
    static final String USER_SCHEMAS = "n.nspname <> 'information_schema' AND n.nspname NOT LIKE 'pg\\_%'";

    /** Copies the patterns. */
    PostgreSqlLookup
    {
        databases = List.copyOf(databases);
        schemas = List.copyOf(schemas);
        tables = List.copyOf(tables);
    }

    /**
     * Builds the lookup of one level.
     *
     * @param resource the level
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most values wanted
     * @return the lookup
     * @throws IllegalArgumentException for a level the service type does not have
     */
    static PostgreSqlLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!List.of(PostgreSqlProvider.DATABASE, PostgreSqlProvider.SCHEMA, PostgreSqlProvider.TABLE, PostgreSqlProvider.COLUMN).contains(resource)) {
            throw new IllegalArgumentException("PostgreSQL services have no resource " + resource);
        }
        return new PostgreSqlLookup(resource, typed.strip(), patterns(context.get(PostgreSqlProvider.DATABASE)),
                patterns(context.get(PostgreSqlProvider.SCHEMA)), patterns(context.get(PostgreSqlProvider.TABLE)), limit);
    }

    /** The patterns of a parent level's values, none when the policy names none, any or too many. */
    private static List<String> patterns(@Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_PARENTS || values.stream().anyMatch(value -> "*".equals(value.strip()))) {
            return List.of();
        }
        return values.stream().map(value -> like(value.strip())).toList();
    }

    /**
     * Turns a policy's wildcard into a {@code LIKE} pattern.
     *
     * @param wildcard the value, with {@code *} for any run of characters and {@code ?} for one
     * @return the pattern
     */
    static String like(String wildcard)
    {
        return wildcard.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace('*', '%').replace('?', '_');
    }

    /**
     * Returns the query of the level, with its parameters in order: what was typed, the patterns of the levels above
     * that narrow it, and the limit.
     *
     * @return the query
     */
    String sql()
    {
        StringBuilder sql = new StringBuilder(320);
        switch (resource) {
            case PostgreSqlProvider.DATABASE -> {
                sql.append("SELECT datname AS value FROM pg_database WHERE datallowconn AND NOT datistemplate AND starts_with(datname, ?)");
                within(sql, "datname", databases);
            }
            case PostgreSqlProvider.SCHEMA -> sql.append("SELECT n.nspname AS value FROM pg_namespace n WHERE starts_with(n.nspname, ?) AND ")
                    .append(USER_SCHEMAS);
            case PostgreSqlProvider.TABLE -> sql.append("SELECT DISTINCT c.relname AS value FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                    + " WHERE c.relkind IN ").append(RELATION_KINDS).append(" AND starts_with(c.relname, ?) AND ").append(USER_SCHEMAS);
            default -> sql.append("SELECT DISTINCT a.attname AS value FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid"
                    + " JOIN pg_namespace n ON n.oid = c.relnamespace WHERE a.attnum > 0 AND NOT a.attisdropped AND c.relkind IN ")
                    .append(RELATION_KINDS).append(" AND starts_with(a.attname, ?) AND ").append(USER_SCHEMAS);
        }
        if (!PostgreSqlProvider.DATABASE.equals(resource)) {
            within(sql, "n.nspname", schemas);
            if (PostgreSqlProvider.COLUMN.equals(resource)) {
                within(sql, "c.relname", tables);
            }
        }
        return sql.append(" ORDER BY value LIMIT ?").toString();
    }

    private static void within(StringBuilder sql, String column, List<String> patterns)
    {
        if (!patterns.isEmpty()) {
            sql.append(" AND ").append(column).append(" LIKE ANY (?)");
        }
    }

    /**
     * Runs the lookup.
     *
     * @param client the servers
     * @param home the database the service signs in to
     * @return the values, sorted
     * @throws LookupException when no server can be reached, one refuses, or no database the lookup reads may be connected to
     */
    List<String> run(PostgreSqlClient client, String home)
    {
        if (PostgreSqlProvider.DATABASE.equals(resource)) {
            return client.run(home, this::values);
        }
        PostgreSqlLookup listing = new PostgreSqlLookup(PostgreSqlProvider.DATABASE, "", databases, List.of(), List.of(), MAX_DATABASES);
        List<String> names = client.run(home, listing::values);
        SortedSet<String> values = new TreeSet<>();
        LookupException refused = null;
        for (String database : names) {
            try {
                values.addAll(client.run(database, this::values));
            }
            catch (LookupException failed) {
                // A database the lookup user may not connect to, or one dropped since it was listed, has nothing to show.
                if (failed.getReason() != LookupException.Reason.ACCESS_DENIED && failed.getReason() != LookupException.Reason.NOT_FOUND) {
                    throw failed;
                }
                refused = failed;
            }
        }
        if (values.isEmpty() && refused != null) {
            throw refused;
        }
        return values.stream().limit(limit).toList();
    }

    /** Runs the query on one database. */
    List<String> values(Connection connection) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(sql())) {
            statement.setString(1, typed);
            int next;
            if (PostgreSqlProvider.DATABASE.equals(resource)) {
                next = bind(connection, statement, 2, databases);
            }
            else {
                next = bind(connection, statement, 2, schemas);
                if (PostgreSqlProvider.COLUMN.equals(resource)) {
                    next = bind(connection, statement, next, tables);
                }
            }
            statement.setInt(next, limit);
            List<String> values = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
            }
            return values;
        }
    }

    private static int bind(Connection connection, PreparedStatement statement, int index, List<String> patterns) throws SQLException
    {
        if (patterns.isEmpty()) {
            return index;
        }
        statement.setArray(index, connection.createArrayOf("text", patterns.toArray()));
        return index + 1;
    }
}
