// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy.persistence.dialect;

import org.devlive.grantforge.server.legacy.LegacyData;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Reads the pre-rebuild database: Flyway V1 named the core tables {@code authx_*}, V2 renamed them to
 * {@code grantforge_*}; either works. Only plain SELECTs of the columns every version had, so any JDBC database the old
 * server ran on can be read.
 */
public final class LegacyDatabase
{
    private LegacyDatabase()
    {
    }

    /**
     * Reads everything GrantForge takes over.
     *
     * @param connection an open connection to the old database; not closed here
     * @return the data
     * @throws SQLException if the database cannot be read
     * @throws IllegalArgumentException if it holds neither naming of the core tables
     */
    public static LegacyData read(Connection connection) throws SQLException
    {
        requireNonNull(connection, "connection");
        Naming naming = naming(connection);
        List<LegacyData.User> users = new ArrayList<>();
        try (ResultSet rows = query(connection, naming.users)) {
            while (rows.next()) {
                users.add(new LegacyData.User(rows.getLong(1), rows.getString(2), rows.getString(3), flag(rows, 4, true), flag(rows, 5, false),
                        flag(rows, 6, false), rows.getString(7)));
            }
        }
        List<LegacyData.Role> roles = new ArrayList<>();
        try (ResultSet rows = query(connection, naming.roles)) {
            while (rows.next()) {
                roles.add(new LegacyData.Role(rows.getLong(1), rows.getString(2), rows.getString(3), rows.getString(4), flag(rows, 5, true)));
            }
        }
        List<LegacyData.Menu> menus = new ArrayList<>();
        try (ResultSet rows = query(connection, naming.menus)) {
            while (rows.next()) {
                long parent = rows.getLong(5);
                menus.add(new LegacyData.Menu(rows.getLong(1), rows.getString(2), rows.getString(3), rows.getInt(4),
                        rows.wasNull() || parent == 0 ? null : parent, rows.getString(6), flag(rows, 7, true)));
            }
        }
        List<LegacyData.Method> methods = new ArrayList<>();
        try (ResultSet rows = query(connection, naming.methods)) {
            while (rows.next()) {
                methods.add(new LegacyData.Method(rows.getLong(1), rows.getString(2)));
            }
        }
        return new LegacyData(users, roles, menus, methods, links(connection, naming.userRoles), links(connection, naming.roleMenus),
                links(connection, naming.menuMethods));
    }

    /** The naming of the core tables, whichever Flyway version the database reached. */
    static Naming naming(Connection connection) throws SQLException
    {
        Set<String> tables = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet found = metadata.getTables(connection.getCatalog(), null, "%", new String[] {"TABLE"})) {
            while (found.next()) {
                tables.add(found.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        for (Naming naming : Naming.values()) {
            if (tables.containsAll(naming.core)) {
                return naming;
            }
        }
        throw new IllegalArgumentException("not an AuthX/GrantForge 1.x database: no grantforge_user/role/menu or authx_user/role/menu tables");
    }

    private static List<LegacyData.Link> links(Connection connection, String sql) throws SQLException
    {
        List<LegacyData.Link> links = new ArrayList<>();
        try (ResultSet rows = query(connection, sql)) {
            while (rows.next()) {
                long first = rows.getLong(1);
                boolean firstMissing = rows.wasNull();
                long second = rows.getLong(2);
                // Rows with an empty side point at nothing; the old server ignored them too.
                if (!firstMissing && !rows.wasNull()) {
                    links.add(new LegacyData.Link(first, second));
                }
            }
        }
        return links;
    }

    // closeOnCompletion: closing the result set closes its statement too.
    @SuppressWarnings("PMD.CloseResource")
    private static ResultSet query(Connection connection, String sql) throws SQLException
    {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.closeOnCompletion();
            return statement.executeQuery();
        }
        catch (SQLException failure) {
            statement.close();
            throw failure;
        }
    }

    /** A TINYINT flag; a missing value takes the default. */
    private static boolean flag(ResultSet rows, int column, boolean fallback) throws SQLException
    {
        int value = rows.getInt(column);
        return rows.wasNull() ? fallback : value != 0;
    }

    /**
     * Describes a JDBC URL without its credentials, for reports.
     *
     * @param url the URL
     * @return the URL up to its parameters
     */
    public static String describe(@Nullable String url)
    {
        if (url == null) {
            return "";
        }
        int parameters = url.indexOf('?');
        String plain = parameters < 0 ? url : url.substring(0, parameters);
        int semicolon = plain.indexOf(';');
        return semicolon < 0 ? plain : plain.substring(0, semicolon);
    }

    /** The two namings of the tables, with the queries of each; fixed text only, never anything read. */
    enum Naming
    {
        /** Flyway V2 and later. */
        GRANTFORGE("grantforge_"),
        /** Flyway V1. */
        AUTHX("authx_");

        private final Set<String> core;
        private final String users;
        private final String roles;
        private final String menus;
        private final String methods;
        private final String userRoles;
        private final String roleMenus;
        private final String menuMethods;

        Naming(String prefix)
        {
            core = Set.of(prefix + "user", prefix + "role", prefix + "menu");
            users = "SELECT id, name, password, active, locked, is_system, email FROM " + prefix + "user ORDER BY id";
            roles = "SELECT id, name, code, description, active FROM " + prefix + "role ORDER BY id";
            menus = "SELECT id, name, url, sorted, parent, description, active FROM " + prefix + "menu ORDER BY id";
            methods = "SELECT id, method FROM " + prefix + "method ORDER BY id";
            userRoles = "SELECT user_id, role_id FROM " + prefix + "user_role_relation";
            roleMenus = "SELECT role_id, menu_id FROM " + prefix + "role_menu_relation";
            menuMethods = "SELECT menu_id, method_id FROM " + prefix + "menu_method_relation";
        }
    }
}
