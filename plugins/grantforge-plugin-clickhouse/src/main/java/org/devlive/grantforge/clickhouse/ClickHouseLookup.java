// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The query that lists the databases, tables or columns whose names start with what was typed, within the databases and
 * tables a policy already names, from ClickHouse's system tables; they show the user only what the user may see. Policy
 * values are wildcards ({@code *} and {@code ?}); they become {@code LIKE} patterns. The server level has its one value
 * and needs no query. ClickHouse's own databases are left out: policies do not decide them.
 *
 * @param sql the query, or {@code null} for the server level
 * @param parameters its parameters, by name
 */
record ClickHouseLookup(@Nullable String sql, Map<String, Object> parameters)
{
    /** The most values of a parent level a lookup is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_PARENTS = 100;

    /** The databases ClickHouse keeps for itself. */
    static final List<String> SYSTEM_DATABASES = List.of("system", "information_schema", "INFORMATION_SCHEMA");

    /**
     * Builds the query for one level.
     *
     * @param resource the level: database, table, column or server
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most values wanted
     * @return the query
     * @throws IllegalArgumentException for a level the service type does not have
     */
    static ClickHouseLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("typed", typed.strip());
        parameters.put("system", SYSTEM_DATABASES);
        parameters.put("limit", limit);
        StringBuilder sql = new StringBuilder(256);
        switch (resource) {
            case ClickHouseProvider.DATABASE -> sql.append("SELECT name AS value FROM system.databases WHERE startsWith(name, {typed:String})"
                    + " AND name NOT IN {system:Array(String)}");
            case ClickHouseProvider.TABLE -> {
                sql.append("SELECT DISTINCT name AS value FROM system.tables WHERE startsWith(name, {typed:String})"
                        + " AND database NOT IN {system:Array(String)} AND NOT is_temporary");
                within(sql, parameters, "database", "databases", context.get(ClickHouseProvider.DATABASE));
            }
            case ClickHouseProvider.COLUMN -> {
                sql.append("SELECT DISTINCT name AS value FROM system.columns WHERE startsWith(name, {typed:String})"
                        + " AND database NOT IN {system:Array(String)}");
                within(sql, parameters, "database", "databases", context.get(ClickHouseProvider.DATABASE));
                within(sql, parameters, "table", "tables", context.get(ClickHouseProvider.TABLE));
            }
            case ClickHouseProvider.SERVER -> {
                return new ClickHouseLookup(null, Map.of("typed", typed.strip()));
            }
            default -> throw new IllegalArgumentException("ClickHouse services have no resource " + resource);
        }
        sql.append(" ORDER BY value LIMIT {limit:UInt32}");
        return new ClickHouseLookup(sql.toString(), Map.copyOf(parameters));
    }

    /** Narrows the query to the values of a parent level, unless the policy names none, any or too many. */
    private static void within(StringBuilder sql, Map<String, Object> parameters, String column, String parameter,
            @Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_PARENTS || values.stream().anyMatch(value -> "*".equals(value.strip()))) {
            return;
        }
        sql.append(" AND arrayExists(pattern -> ").append(column).append(" LIKE pattern, {").append(parameter).append(":Array(String)})");
        parameters.put(parameter, values.stream().map(value -> like(value.strip())).toList());
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
     * Runs the lookup.
     *
     * @param client the servers
     * @return the values in the server's order
     */
    List<String> run(ClickHouseNodeClient client)
    {
        if (sql == null) {
            return "*".startsWith(String.valueOf(parameters.get("typed"))) ? List.of("*") : List.of();
        }
        return ClickHouseNodeClient.texts(client.query(sql, parameters), "value");
    }
}
