// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The service type's access types and the Doris privileges each stands for, both ways: what the agent grants for an
 * access type, and which access types a privilege Doris shows or asks for amounts to.
 */
final class Privileges
{
    static final String SELECT = "select";
    static final String LOAD = "load";
    static final String ALTER = "alter";
    static final String DROP = "drop";
    static final String SHOW_VIEW = "show_view";
    static final String CREATE = "create";
    static final String DROP_DATABASE = "drop_database";
    static final String CREATE_DATABASE = "create_database";
    static final String USAGE = "usage";

    /** The access types of tables, which Doris grants on databases and catalogs too, {@code select} also on columns. */
    static final List<String> TABLE_TYPES = List.of(SELECT, LOAD, ALTER, DROP, SHOW_VIEW);
    /** The table access types the agent grants on a database or catalog as a whole where nothing below decides otherwise. */
    static final List<String> LIFTED_TYPES = List.of(SELECT, LOAD, SHOW_VIEW);

    /** Doris's privilege for each access type, as GRANT names it. */
    private static final Map<String, String> PRIVILEGE = Map.of(SELECT, "SELECT_PRIV", LOAD, "LOAD_PRIV", ALTER, "ALTER_PRIV", DROP, "DROP_PRIV",
            SHOW_VIEW, "SHOW_VIEW_PRIV", CREATE, "CREATE_PRIV", DROP_DATABASE, "DROP_PRIV", CREATE_DATABASE, "CREATE_PRIV", USAGE, "USAGE_PRIV");

    private Privileges()
    {
    }

    /**
     * Returns the privilege the agent grants for an access type.
     *
     * @param accessType the access type
     * @return Doris's name of it, such as {@code SELECT_PRIV}
     * @throws IllegalArgumentException for an access type the service type does not have
     */
    static String privilege(String accessType)
    {
        String privilege = PRIVILEGE.get(accessType);
        if (privilege == null) {
            throw new IllegalArgumentException("Doris services have no access type " + accessType);
        }
        return privilege;
    }

    /**
     * Returns the access types a privilege amounts to on a level, as Doris shows or names it. {@code ADMIN} amounts to
     * every access type; a privilege no access type stands for, such as {@code NODE} or {@code GRANT}, to none.
     *
     * @param privilege such as {@code Select_priv}, {@code SELECT} or {@code DROP}
     * @param level catalog, database, table or workload_group
     * @return the access types
     */
    static Set<String> accessTypes(String privilege, String level)
    {
        String name = privilege.strip().toUpperCase(Locale.ROOT).replaceFirst("_PRIV$", "").replace(' ', '_');
        Set<String> types = new LinkedHashSet<>();
        switch (name) {
            case "ADMIN" -> types.addAll(PRIVILEGE.keySet());
            case "SELECT" -> types.add(SELECT);
            case "LOAD", "INSERT", "DELETE", "UPDATE" -> types.add(LOAD);
            case "ALTER" -> types.add(ALTER);
            case "SHOW_VIEW" -> types.add(SHOW_VIEW);
            case "USAGE" -> types.add(USAGE);
            case "DROP" -> {
                types.add(DROP);
                if (!"table".equals(level)) {
                    types.add(DROP_DATABASE);
                }
            }
            case "CREATE" -> {
                types.add(CREATE);
                if ("catalog".equals(level)) {
                    types.add(CREATE_DATABASE);
                }
            }
            default -> {
                // NODE, GRANT and the privileges of resources, storage vaults and compute groups stand for no access type.
            }
        }
        return types;
    }
}
