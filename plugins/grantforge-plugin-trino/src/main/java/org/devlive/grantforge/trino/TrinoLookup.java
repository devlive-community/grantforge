// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Lists the catalogs, schemas, tables, columns and session properties whose names start with what was typed, within
 * the catalogs, schemas and tables a policy names already. Trino keeps names in lower case and compares them without
 * case. Catalogs come first: the driver's metadata takes a catalog by name, so the policy's catalog values are matched
 * against Trino's catalogs, and the values below become {@code LIKE} patterns.
 *
 * @param resource the level
 * @param typed what was typed, in lower case
 * @param catalogs the catalog values the policy names, or {@code *}
 * @param schemas the schema values, or {@code *}
 * @param tables the table values, or {@code *}
 * @param limit the most names returned
 */
record TrinoLookup(String resource, String typed, List<String> catalogs, List<String> schemas, List<String> tables, int limit)
{
    /** The most values of a parent level a lookup is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_PARENTS = 50;

    private static final List<String> ANY = List.of("*");

    /**
     * Builds the lookup of one level.
     *
     * @param resource catalog, schema, table, column, session_property or system_session_property
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most names returned
     * @return the lookup
     * @throws IllegalArgumentException for a level without lookups
     */
    static TrinoLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!List.of(TrinoProvider.CATALOG, TrinoProvider.SCHEMA, TrinoProvider.TABLE, TrinoProvider.COLUMN, TrinoProvider.SESSION_PROPERTY,
                TrinoProvider.SYSTEM_SESSION_PROPERTY).contains(resource)) {
            throw new IllegalArgumentException("Trino services look up catalogs, schemas, tables, columns and session properties, not " + resource);
        }
        return new TrinoLookup(resource, typed.strip().toLowerCase(Locale.ROOT), values(context.get(TrinoProvider.CATALOG)),
                values(context.get(TrinoProvider.SCHEMA)), values(context.get(TrinoProvider.TABLE)), limit);
    }

    /** A policy's values; none, any or too many look everywhere. */
    private static List<String> values(@Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_PARENTS || values.stream().anyMatch(value -> "*".equals(value.strip()))) {
            return ANY;
        }
        return values.stream().map(value -> value.strip().toLowerCase(Locale.ROOT)).distinct().toList();
    }

    /**
     * Turns a policy's wildcard into a pattern of names.
     *
     * @param wildcard the value, with {@code *} for any run of characters and {@code ?} for one
     * @return the pattern
     */
    static Pattern pattern(String wildcard)
    {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < wildcard.length(); index++) {
            char character = wildcard.charAt(index);
            regex.append(character == '*' ? ".*" : character == '?' ? "." : Pattern.quote(String.valueOf(character)));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    /**
     * Turns a policy's wildcard into a {@code LIKE} pattern, escaping what {@code LIKE} reads otherwise.
     *
     * @param wildcard the value
     * @param escape the driver's escape
     * @return the pattern
     */
    static String like(String wildcard, String escape)
    {
        StringBuilder like = new StringBuilder();
        for (int index = 0; index < wildcard.length(); index++) {
            char character = wildcard.charAt(index);
            if (character == '*') {
                like.append('%');
            }
            else if (character == '?') {
                like.append('_');
            }
            else {
                if (character == '%' || character == '_' || escape.equals(String.valueOf(character))) {
                    like.append(escape);
                }
                like.append(character);
            }
        }
        return like.toString();
    }

    /**
     * Runs the lookup.
     *
     * @param connection the connection
     * @return the names, sorted, at most {@link #limit()}
     * @throws SQLException when Trino fails a request
     */
    List<String> run(Connection connection) throws SQLException
    {
        SortedSet<String> names = new TreeSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        String escape = String.valueOf(metadata.getSearchStringEscape());
        switch (resource) {
            case TrinoProvider.CATALOG -> collect(metadata.getCatalogs(), "TABLE_CAT", names);
            case TrinoProvider.SCHEMA -> {
                for (String catalog : catalogs(metadata)) {
                    collect(metadata.getSchemas(catalog, like(typed, escape) + "%"), "TABLE_SCHEM", names);
                }
            }
            case TrinoProvider.TABLE -> {
                for (String catalog : catalogs(metadata)) {
                    for (String schema : schemas) {
                        collect(metadata.getTables(catalog, like(schema, escape), like(typed, escape) + "%", null), "TABLE_NAME", names);
                    }
                }
            }
            case TrinoProvider.COLUMN -> {
                for (String catalog : catalogs(metadata)) {
                    for (String schema : schemas) {
                        for (String table : tables) {
                            collect(metadata.getColumns(catalog, like(schema, escape), like(table, escape), like(typed, escape) + "%"),
                                    "COLUMN_NAME", names);
                        }
                    }
                }
            }
            default -> sessionProperties(connection, names);
        }
        List<String> found = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith(typed) && found.size() < limit) {
                found.add(name);
            }
        }
        return found;
    }

    /** The catalogs the policy names, as Trino has them. */
    private List<String> catalogs(DatabaseMetaData metadata) throws SQLException
    {
        SortedSet<String> all = new TreeSet<>();
        collect(metadata.getCatalogs(), "TABLE_CAT", all);
        List<Pattern> patterns = catalogs.stream().map(TrinoLookup::pattern).toList();
        List<String> matching = new ArrayList<>();
        for (String catalog : all) {
            if (matching.size() < MAX_PARENTS && patterns.stream().anyMatch(pattern -> pattern.matcher(catalog).matches())) {
                matching.add(catalog);
            }
        }
        return matching;
    }

    /** Session properties: a catalog's are named catalog.property, the system's have no catalog. */
    private void sessionProperties(Connection connection, SortedSet<String> names) throws SQLException
    {
        boolean system = TrinoProvider.SYSTEM_SESSION_PROPERTY.equals(resource);
        List<Pattern> patterns = catalogs.stream().map(TrinoLookup::pattern).toList();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SHOW SESSION")) {
            while (rows.next()) {
                String name = rows.getString("Name");
                int dot = name == null ? -1 : name.indexOf('.');
                if (name == null || system != (dot < 0)) {
                    continue;
                }
                if (system) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
                else if (patterns.stream().anyMatch(pattern -> pattern.matcher(name.substring(0, dot)).matches())) {
                    names.add(name.substring(dot + 1).toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private static void collect(ResultSet rows, String column, SortedSet<String> names) throws SQLException
    {
        try (rows) {
            while (rows.next()) {
                String name = rows.getString(column);
                if (name != null) {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
    }
}
