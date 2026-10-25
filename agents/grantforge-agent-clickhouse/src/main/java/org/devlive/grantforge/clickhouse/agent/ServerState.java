// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What a ClickHouse server holds at one moment that the agent looks at: the users it may keep roles for and those it
 * may not, the roles and row policies it keeps, the privileges of its roles, the grants it did not make, and the
 * databases, tables and columns.
 *
 * @param self the agent's own user
 * @param accounts the users whose access ClickHouse stores where the agent can change it, but the agent and the users
 *        it leaves alone
 * @param readOnly the users stored where nothing can change them, such as users.xml or LDAP
 * @param roles the agent's roles
 * @param privileges what each of the agent's roles holds, as one text that changes with it
 * @param rowPolicies the agent's row policies
 * @param nativeGrants the grants the agent did not make, of users and of roles
 * @param inventory the databases, tables and columns
 */
record ServerState(String self, List<Account> accounts, List<String> readOnly, Set<String> roles, Map<String, String> privileges,
        Map<RowPolicyKey, RowPolicy> rowPolicies, List<NativeGrant> nativeGrants, Inventory inventory)
{
    /** The kinds of user directories whose users, roles and row policies can be changed. */
    static final Set<String> WRITABLE = Set.of("local_directory", "replicated", "memory");

    /** The databases ClickHouse keeps for itself. */
    static final List<String> SYSTEM_DATABASES = List.of("system", "information_schema", "INFORMATION_SCHEMA");

    /** Copies everything. */
    ServerState
    {
        accounts = List.copyOf(accounts);
        readOnly = List.copyOf(readOnly);
        roles = Set.copyOf(roles);
        privileges = Map.copyOf(privileges);
        rowPolicies = Map.copyOf(rowPolicies);
        nativeGrants = List.copyOf(nativeGrants);
    }

    /**
     * Names one of the agent's row policies: its role and its table.
     *
     * @param role the role, which is also the policy's name
     * @param table the table
     */
    record RowPolicyKey(String role, TableRef table)
    {
    }

    /**
     * A row policy as ClickHouse shows it.
     *
     * @param filter the condition, as ClickHouse writes it back
     * @param restrictive whether it is restrictive rather than permissive
     * @param toAll whether it applies to everybody, but {@code except}
     * @param to whom it applies to
     * @param except whom it does not apply to
     */
    record RowPolicy(String filter, boolean restrictive, boolean toAll, List<String> to, List<String> except)
    {
        /** Copies the names. */
        RowPolicy
        {
            to = List.copyOf(to);
            except = List.copyOf(except);
        }

        /**
         * Returns whether the policy is permissive and applies to the one role alone.
         *
         * @param role the role
         * @return {@code true} if so
         */
        boolean onlyFor(String role)
        {
            return !restrictive && !toAll && except.isEmpty() && to.equals(List.of(role));
        }
    }

    /**
     * A grant of a user or of a role that the agent did not make.
     *
     * @param user the user that holds it, or {@code null}
     * @param role the role that holds it, or {@code null}
     * @param privilege the privilege, such as {@code SELECT}
     * @param database the database, or {@code null} for all
     * @param table the table, or {@code null} for all
     * @param column the column, or {@code null} for all
     */
    record NativeGrant(@Nullable String user, @Nullable String role, String privilege, @Nullable String database, @Nullable String table,
            @Nullable String column)
    {
        /**
         * Returns the grant as ClickHouse writes it.
         *
         * @return such as {@code SELECT(amount) ON hr.salaries}
         */
        String text()
        {
            return privilege + (column == null ? "" : "(" + column + ")") + " ON " + (database == null ? "*" : database) + "."
                    + (table == null ? "*" : table);
        }
    }

    /**
     * Reads the state.
     *
     * @param clickhouse the server
     * @param prefix the start of the agent's roles' names
     * @param unmanaged the users the agent leaves alone
     * @return the state
     * @throws ClickHouseFailure when the server fails a query
     */
    // A value per row read is what reading the system tables is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static ServerState read(ClickHouse clickhouse, String prefix, Set<String> unmanaged)
    {
        Map<String, Object> byPrefix = Map.of("prefix", prefix);
        String self = clickhouse.query("SELECT currentUser() AS user", Map.of()).get(0).string("user");

        Map<String, Set<String>> rolesOfUser = new HashMap<>();
        Map<String, Set<String>> rolesOfRole = new HashMap<>();
        for (Row row : clickhouse.query("SELECT user_name, role_name, granted_role_name FROM system.role_grants", Map.of())) {
            String user = row.text("user_name");
            String holder = row.text("role_name");
            Map<String, Set<String>> into = user != null ? rolesOfUser : rolesOfRole;
            String key = user != null ? user : holder;
            if (key != null) {
                into.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(row.string("granted_role_name"));
            }
        }

        List<Account> accounts = new ArrayList<>();
        List<String> readOnly = new ArrayList<>();
        for (Row row : clickhouse.query("SELECT u.name AS name, toString(d.type) AS directory, u.default_roles_all AS default_all,"
                + " u.default_roles_list AS default_list, u.default_roles_except AS default_except FROM system.users AS u"
                + " LEFT JOIN system.user_directories AS d ON u.storage = d.name ORDER BY name", Map.of())) {
            String name = row.string("name");
            if (name.equals(self) || unmanaged.contains(name)) {
                continue;
            }
            if (!WRITABLE.contains(row.string("directory"))) {
                readOnly.add(name);
                continue;
            }
            Set<String> granted = rolesOfUser.getOrDefault(name, Set.of());
            accounts.add(new Account(name, held(granted, rolesOfRole, prefix), granted, row.flag("default_all"), row.texts("default_list"),
                    row.texts("default_except")));
        }

        Set<String> roles = new LinkedHashSet<>();
        for (Row row : clickhouse.query("SELECT name FROM system.roles WHERE startsWith(name, {prefix:String})", byPrefix)) {
            roles.add(row.string("name"));
        }
        Map<String, String> privileges = privileges(clickhouse, prefix);

        Map<RowPolicyKey, RowPolicy> rowPolicies = new LinkedHashMap<>();
        for (Row row : clickhouse.query("SELECT short_name, database, table, select_filter, is_restrictive, apply_to_all, apply_to_list,"
                + " apply_to_except FROM system.row_policies WHERE startsWith(short_name, {prefix:String})", byPrefix)) {
            rowPolicies.put(new RowPolicyKey(row.string("short_name"), new TableRef(row.string("database"), row.string("table"))),
                    new RowPolicy(row.string("select_filter"), row.flag("is_restrictive"), row.flag("apply_to_all"), row.texts("apply_to_list"),
                            row.texts("apply_to_except")));
        }

        List<NativeGrant> nativeGrants = new ArrayList<>();
        if (!accounts.isEmpty()) {
            for (Row row : clickhouse.query("SELECT user_name, role_name, toString(access_type) AS access_type, database, table, column"
                    + " FROM system.grants WHERE NOT is_partial_revoke AND (role_name IS NULL OR NOT startsWith(role_name, {prefix:String}))", byPrefix)) {
                nativeGrants.add(new NativeGrant(row.text("user_name"), row.text("role_name"), row.string("access_type"), row.text("database"),
                        row.text("table"), row.text("column")));
            }
        }
        return new ServerState(self, accounts, readOnly, roles, privileges, rowPolicies, nativeGrants, inventory(clickhouse));
    }

    /**
     * Reads what each of the agent's roles holds.
     *
     * @param clickhouse the server
     * @param prefix the start of the agent's roles' names
     * @return one text per role that changes with its privileges, by role
     * @throws ClickHouseFailure when the server fails the query
     */
    // A list per role that holds privileges.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static Map<String, String> privileges(ClickHouse clickhouse, String prefix)
    {
        Map<String, List<String>> held = new TreeMap<>();
        for (Row row : clickhouse.query("SELECT role_name, toString(access_type) AS access_type, database, table, column, is_partial_revoke,"
                + " grant_option FROM system.grants WHERE startsWith(role_name, {prefix:String})", Map.of("prefix", prefix))) {
            held.computeIfAbsent(row.string("role_name"), ignored -> new ArrayList<>()).add(String.join("\u001f", row.string("access_type"),
                    String.valueOf(row.text("database")), String.valueOf(row.text("table")), String.valueOf(row.text("column")),
                    Boolean.toString(row.flag("is_partial_revoke")), Boolean.toString(row.flag("grant_option"))));
        }
        Map<String, String> privileges = new HashMap<>();
        held.forEach((role, rows) -> {
            Collections.sort(rows);
            privileges.put(role, String.join("\u001e", rows));
        });
        return privileges;
    }

    /** The roles a user holds through the roles granted to it, the agent's own left out. */
    private static Set<String> held(Set<String> granted, Map<String, Set<String>> rolesOfRole, String prefix)
    {
        Set<String> held = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(granted);
        while (!pending.isEmpty()) {
            String role = pending.pop();
            if (!role.startsWith(prefix) && held.add(role)) {
                pending.addAll(rolesOfRole.getOrDefault(role, Set.of()));
            }
        }
        return held;
    }

    // A list per database and table.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Inventory inventory(ClickHouse clickhouse)
    {
        Map<String, Object> system = Map.of("system", SYSTEM_DATABASES);
        Map<String, List<String>> tables = new TreeMap<>();
        for (Row row : clickhouse.query("SELECT name FROM system.databases WHERE name NOT IN {system:Array(String)}", system)) {
            tables.put(row.string("name"), new ArrayList<>());
        }
        for (Row row : clickhouse.query("SELECT database, name FROM system.tables WHERE database NOT IN {system:Array(String)} AND NOT is_temporary"
                + " ORDER BY database, name", system)) {
            tables.computeIfAbsent(row.string("database"), ignored -> new ArrayList<>()).add(row.string("name"));
        }
        return new Inventory(tables, database -> {
            Map<String, List<String>> columns = new HashMap<>();
            for (Row row : clickhouse.query("SELECT table, name FROM system.columns WHERE database = {database:String} ORDER BY table, position",
                    Map.of("database", database))) {
                columns.computeIfAbsent(row.string("table"), ignored -> new ArrayList<>()).add(row.string("name"));
            }
            return columns;
        });
    }
}
