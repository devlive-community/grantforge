// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Lists the databases, tables, columns and UDFs whose names start with what was typed, within the databases and tables
 * a policy names already, through the driver's database metadata, which HiveServer2 answers from its metastore. Hive's
 * patterns know {@code %} and {@code _} only, so policy wildcards become them and what comes back is matched again
 * here; names compare without case, as Hive stores them in lower case.
 *
 * @param resource the level
 * @param typed what was typed, in lower case
 * @param databases the database patterns to look in
 * @param tables the table patterns to look in
 * @param limit the most names returned
 */
record HiveLookup(String resource, String typed, List<String> databases, List<String> tables, int limit)
{
    /** The most values of a parent level a lookup is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_PARENTS = 50;

    /**
     * Builds the lookup of one level.
     *
     * @param resource database, table, column or udf
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most names returned
     * @return the lookup
     * @throws IllegalArgumentException for a level without lookups
     */
    static HiveLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!HiveProvider.DATABASE.equals(resource) && !HiveProvider.TABLE.equals(resource) && !HiveProvider.COLUMN.equals(resource)
                && !HiveProvider.UDF.equals(resource)) {
            throw new IllegalArgumentException("Hive services look up databases, tables, columns and UDFs, not " + resource);
        }
        return new HiveLookup(resource, typed.strip().toLowerCase(Locale.ROOT), patterns(context.get(HiveProvider.DATABASE)),
                patterns(context.get(HiveProvider.TABLE)), limit);
    }

    /** A policy's values as metadata patterns; none, any or too many look everywhere. */
    private static List<String> patterns(@Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_PARENTS || values.stream().anyMatch(value -> "*".equals(value.strip()))) {
            return List.of("%");
        }
        return values.stream().map(value -> pattern(value.strip())).distinct().toList();
    }

    /**
     * Turns a policy's wildcard into a metadata pattern.
     *
     * @param wildcard the value, with {@code *} for any run of characters and {@code ?} for one
     * @return the pattern
     */
    static String pattern(String wildcard)
    {
        return wildcard.toLowerCase(Locale.ROOT).replace('*', '%').replace('?', '_');
    }

    /**
     * Runs the lookup.
     *
     * @param connection the connection
     * @return the names, sorted
     * @throws SQLException when the server fails it
     */
    List<String> run(Connection connection) throws SQLException
    {
        DatabaseMetaData metadata = connection.getMetaData();
        SortedSet<String> names = new TreeSet<>();
        String like = typed + "%";
        switch (resource) {
            case HiveProvider.DATABASE -> collect(metadata.getSchemas(null, like), "TABLE_SCHEM", names);
            case HiveProvider.TABLE -> {
                for (String database : databases) {
                    collect(metadata.getTables(null, database, like, null), "TABLE_NAME", names);
                }
            }
            case HiveProvider.COLUMN -> {
                for (String database : databases) {
                    for (String table : tables) {
                        collect(metadata.getColumns(null, database, table, like), "COLUMN_NAME", names);
                    }
                }
            }
            default -> {
                // HiveServer2 names permanent functions database.function, with no schema of their own.
                for (String database : databases) {
                    collect(metadata.getFunctions(null, null, database + "." + like), "FUNCTION_NAME", names);
                }
            }
        }
        List<String> found = new ArrayList<>();
        for (String name : names) {
            if (found.size() == limit) {
                break;
            }
            found.add(name);
        }
        return found;
    }

    private void collect(ResultSet rows, String column, SortedSet<String> names) throws SQLException
    {
        try (rows) {
            while (rows.next()) {
                String name = rows.getString(column);
                if (name == null) {
                    continue;
                }
                boolean udf = HiveProvider.UDF.equals(resource);
                if (udf && name.indexOf('.') < 0) {
                    // A built-in function, which no policy grants.
                    continue;
                }
                String simple = udf ? name.substring(name.lastIndexOf('.') + 1) : name;
                String lower = simple.toLowerCase(Locale.ROOT);
                if (lower.startsWith(typed)) {
                    names.add(lower);
                }
            }
        }
    }
}
