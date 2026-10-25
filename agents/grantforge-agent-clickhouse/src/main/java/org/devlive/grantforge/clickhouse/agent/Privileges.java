// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The service type's access types and the ClickHouse privileges each stands for, both ways: what the agent grants for
 * an access type, and which access types a privilege ClickHouse shows, grants or asks for amounts to.
 */
final class Privileges
{
    static final String SELECT = "select";
    static final String INSERT = "insert";
    static final String ALTER = "alter";
    static final String DROP = "drop";
    static final String TRUNCATE = "truncate";
    static final String OPTIMIZE = "optimize";
    static final String CREATE = "create";
    static final String DROP_DATABASE = "drop_database";
    static final String CREATE_DATABASE = "create_database";
    static final String KILL_QUERY = "kill_query";
    static final String SYSTEM = "system";

    /** The access types of columns, which ClickHouse grants on columns too. */
    static final List<String> COLUMN_TYPES = List.of(SELECT, INSERT);
    /** The access types of tables. */
    static final List<String> TABLE_TYPES = List.of(ALTER, DROP, TRUNCATE, OPTIMIZE);
    /** The access types of databases. */
    static final List<String> DATABASE_TYPES = List.of(CREATE, DROP_DATABASE);
    /** The access types of the server. */
    static final List<String> SERVER_TYPES = List.of(CREATE_DATABASE, KILL_QUERY, SYSTEM);

    /** What ALTER privileges of users, roles, policies, quotas, profiles and named collections name; they alter no table. */
    private static final Pattern ACCESS_ENTITIES = Pattern.compile("\\b(USER|ROLE|POLICY|QUOTA|PROFILE|COLLECTION)\\b");

    /** ClickHouse's privileges for each access type, as GRANT names them. */
    private static final Map<String, List<String>> KEYWORDS = keywords();

    private Privileges()
    {
    }

    private static Map<String, List<String>> keywords()
    {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put(SELECT, List.of("SELECT"));
        keywords.put(INSERT, List.of("INSERT"));
        keywords.put(ALTER, List.of("ALTER"));
        keywords.put(DROP, List.of("DROP TABLE", "DROP VIEW", "DROP DICTIONARY"));
        keywords.put(TRUNCATE, List.of("TRUNCATE"));
        keywords.put(OPTIMIZE, List.of("OPTIMIZE"));
        keywords.put(CREATE, List.of("CREATE TABLE", "CREATE VIEW", "CREATE DICTIONARY"));
        keywords.put(DROP_DATABASE, List.of("DROP DATABASE"));
        keywords.put(CREATE_DATABASE, List.of("CREATE DATABASE"));
        keywords.put(KILL_QUERY, List.of("KILL QUERY"));
        keywords.put(SYSTEM, List.of("SYSTEM"));
        return keywords;
    }

    /**
     * Returns the privileges the agent grants for an access type.
     *
     * @param accessType the access type
     * @return ClickHouse's names of them
     * @throws IllegalArgumentException for an access type the service type does not have
     */
    static List<String> keywords(String accessType)
    {
        List<String> keywords = KEYWORDS.get(accessType);
        if (keywords == null) {
            throw new IllegalArgumentException("ClickHouse services have no access type " + accessType);
        }
        return keywords;
    }

    /**
     * Returns the access types a privilege amounts to, as {@code system.grants} or an error message names it. A group
     * such as {@code ALL}, {@code ALTER} or {@code DROP} amounts to each access type of it; a privilege no access type
     * stands for amounts to none.
     *
     * @param privilege such as {@code SELECT}, {@code ALTER UPDATE} or {@code DROP TABLE}
     * @return the access types
     */
    static Set<String> accessTypes(String privilege)
    {
        String name = privilege.strip().toUpperCase(Locale.ROOT);
        Set<String> types = new LinkedHashSet<>();
        switch (name) {
            case "ALL" -> types.addAll(KEYWORDS.keySet());
            case "SELECT" -> types.add(SELECT);
            case "INSERT" -> types.add(INSERT);
            case "TRUNCATE" -> types.add(TRUNCATE);
            case "OPTIMIZE" -> types.add(OPTIMIZE);
            case "DROP" -> types.addAll(List.of(DROP, DROP_DATABASE));
            case "DROP TABLE", "DROP VIEW", "DROP DICTIONARY" -> types.add(DROP);
            case "DROP DATABASE" -> types.add(DROP_DATABASE);
            case "CREATE" -> types.addAll(List.of(CREATE, CREATE_DATABASE));
            case "CREATE TABLE", "CREATE VIEW", "CREATE DICTIONARY", "CREATE TEMPORARY TABLE" -> types.add(CREATE);
            case "CREATE DATABASE" -> types.add(CREATE_DATABASE);
            case "KILL QUERY" -> types.add(KILL_QUERY);
            default -> {
                if ("ALTER".equals(name) || name.startsWith("ALTER ") && !ACCESS_ENTITIES.matcher(name).find()) {
                    types.add(ALTER);
                }
                else if ("SYSTEM".equals(name) || name.startsWith("SYSTEM ")) {
                    types.add(SYSTEM);
                }
            }
        }
        return types;
    }
}
