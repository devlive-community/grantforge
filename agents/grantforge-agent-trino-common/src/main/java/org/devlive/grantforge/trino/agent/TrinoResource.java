// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Something Trino checks, as the levels of the Trino service type name it, from the top. Catalogs, schemas, tables and
 * columns form one tree whose permissions cover what is within them, so they are decided at every scope; routines and
 * session properties are granted by naming them and are decided where they are.
 */
public final class TrinoResource
{
    static final String CATALOG = "catalog";
    static final String SCHEMA = "schema";
    static final String TABLE = "table";
    static final String COLUMN = "column";
    static final String FUNCTION = "function";
    static final String PROCEDURE = "procedure";
    static final String SESSION_PROPERTY = "session_property";
    static final String SYSTEM_SESSION_PROPERTY = "system_session_property";
    static final String USER = "user";
    static final String SYSTEM_INFORMATION = "system_information";

    /** The one value of the system information level. */
    static final String SYSTEM_VALUE = "*";

    private final String level;
    private final Map<String, String> path;
    private final boolean tree;

    private TrinoResource(String level, Map<String, String> path, boolean tree)
    {
        this.level = level;
        this.path = Collections.unmodifiableMap(path);
        this.tree = tree;
    }

    private static TrinoResource of(boolean tree, String... levels)
    {
        Map<String, String> path = new LinkedHashMap<>();
        for (int index = 0; index < levels.length; index += 2) {
            path.put(levels[index], requireNonNull(levels[index + 1], levels[index]));
        }
        return new TrinoResource(levels[levels.length - 2], path, tree);
    }

    /**
     * A catalog.
     *
     * @param catalog its name
     * @return the resource
     */
    public static TrinoResource catalog(String catalog)
    {
        return of(true, CATALOG, catalog);
    }

    /**
     * A schema.
     *
     * @param catalog the catalog
     * @param schema the schema
     * @return the resource
     */
    public static TrinoResource schema(String catalog, String schema)
    {
        return of(true, CATALOG, catalog, SCHEMA, schema);
    }

    /**
     * A table, view or materialized view.
     *
     * @param catalog the catalog
     * @param schema the schema
     * @param table the table
     * @return the resource
     */
    public static TrinoResource table(String catalog, String schema, String table)
    {
        return of(true, CATALOG, catalog, SCHEMA, schema, TABLE, table);
    }

    /**
     * A column.
     *
     * @param catalog the catalog
     * @param schema the schema
     * @param table the table
     * @param column the column
     * @return the resource
     */
    public static TrinoResource column(String catalog, String schema, String table, String column)
    {
        return of(true, CATALOG, catalog, SCHEMA, schema, TABLE, table, COLUMN, column);
    }

    /**
     * A function.
     *
     * @param catalog the catalog
     * @param schema the schema
     * @param function the function
     * @return the resource
     */
    public static TrinoResource function(String catalog, String schema, String function)
    {
        return of(false, CATALOG, catalog, SCHEMA, schema, FUNCTION, function);
    }

    /**
     * A procedure.
     *
     * @param catalog the catalog
     * @param schema the schema
     * @param procedure the procedure
     * @return the resource
     */
    public static TrinoResource procedure(String catalog, String schema, String procedure)
    {
        return of(false, CATALOG, catalog, SCHEMA, schema, PROCEDURE, procedure);
    }

    /**
     * A catalog's session property.
     *
     * @param catalog the catalog
     * @param property the property, without the catalog
     * @return the resource
     */
    public static TrinoResource sessionProperty(String catalog, String property)
    {
        return of(false, CATALOG, catalog, SESSION_PROPERTY, property);
    }

    /**
     * A system session property.
     *
     * @param property the property
     * @return the resource
     */
    public static TrinoResource systemSessionProperty(String property)
    {
        return of(false, SYSTEM_SESSION_PROPERTY, property);
    }

    /**
     * A user, to impersonate or whose queries to see or kill.
     *
     * @param user the user
     * @return the resource
     */
    public static TrinoResource user(String user)
    {
        return of(false, USER, user);
    }

    /**
     * The coordinator's system information.
     *
     * @return the resource
     */
    public static TrinoResource systemInformation()
    {
        return of(false, SYSTEM_INFORMATION, SYSTEM_VALUE);
    }

    /**
     * Returns the level the resource is of.
     *
     * @return such as {@code table}
     */
    public String level()
    {
        return level;
    }

    /**
     * Returns the levels and values that name the resource, from the top.
     *
     * @return them
     */
    public Map<String, String> path()
    {
        return path;
    }

    /**
     * Returns the scopes the resource is decided at: from its catalog down for the catalog tree, where a permission on a
     * scope covers what is within it, or only where it is.
     *
     * @return the scopes, each the levels and values from the top
     */
    List<Map<String, String>> scopes()
    {
        if (!tree) {
            return List.of(path);
        }
        List<Map<String, String>> scopes = new ArrayList<>();
        Map<String, String> scope = new LinkedHashMap<>();
        for (Map.Entry<String, String> level : path.entrySet()) {
            scope.put(level.getKey(), level.getValue());
            scopes.add(Collections.unmodifiableMap(new LinkedHashMap<>(scope)));
        }
        return scopes;
    }

    /**
     * Returns the value at a level.
     *
     * @param name the level
     * @return the value, or {@code null} when the resource does not name it
     */
    @Nullable String value(String name)
    {
        return path.get(name);
    }

    /**
     * Returns how audit events and refusals name the resource.
     *
     * @return such as {@code hive.sales.orders.amount}, or {@code user alice}
     */
    String display()
    {
        if (tree || FUNCTION.equals(level) || PROCEDURE.equals(level)) {
            return String.join(".", path.values());
        }
        if (SESSION_PROPERTY.equals(level)) {
            return "session property " + String.join(".", path.values());
        }
        if (SYSTEM_SESSION_PROPERTY.equals(level)) {
            return "system session property " + path.get(level);
        }
        if (USER.equals(level)) {
            return "user " + path.get(level);
        }
        return "system information";
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof TrinoResource resource && path.equals(resource.path) && level.equals(resource.level);
    }

    @Override
    public int hashCode()
    {
        return path.hashCode();
    }

    @Override
    public String toString()
    {
        return display();
    }
}
