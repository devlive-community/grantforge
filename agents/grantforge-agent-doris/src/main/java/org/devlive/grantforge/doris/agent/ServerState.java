// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a Doris cluster holds at one moment that the agent looks at: the users it may keep roles for, the users no role
 * binds, the agent's roles and what they hold, the other roles, what each user may do through any grant, the row
 * policies, and the catalogs, databases, tables, columns and workload groups.
 *
 * @param self the agent's own user name
 * @param accounts the users the agent may keep roles for, but itself and the users it leaves alone
 * @param superusers the users with {@code ADMIN_PRIV} or {@code NODE_PRIV}, whom no role binds
 * @param roles what each of the agent's roles holds on objects as a whole, by role
 * @param otherRoles what each other role holds on objects as a whole, by role
 * @param userPrivileges what each identity may do on objects as a whole through any grant, by identity
 * @param rowPolicies the row policies, the agent's and others
 * @param inventory the catalogs, databases, tables, columns and workload groups
 */
record ServerState(String self, List<Account> accounts, List<String> superusers, Map<String, Set<Privilege>> roles,
        Map<String, Set<Privilege>> otherRoles, Map<String, Set<Privilege>> userPrivileges, Map<RowPolicyKey, RowPolicy> rowPolicies,
        Inventory inventory)
{
    /** The users Doris keeps for itself, whom no row policy can bind. */
    static final Set<String> BUILT_IN_USERS = Set.of("root", "admin");

    /** The roles Doris keeps for itself. */
    static final Set<String> BUILT_IN_ROLES = Set.of("admin", "operator");

    /** The databases Doris keeps for itself; policies do not decide them. */
    static final Set<String> SYSTEM_DATABASES = Set.of("information_schema", "mysql", "__internal_schema");

    /** A user identity as Doris writes it: {@code 'name'@'host'} or {@code 'name'@['domain']}. */
    private static final Pattern IDENTITY = Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'@(?:'.*'|\\[.*])");

    /** One column privilege as {@code SHOW ALL GRANTS} shows it: {@code Select_priv[Region, id]}. */
    private static final Pattern COLUMNS = Pattern.compile("([A-Za-z_]+)\\[(.*)]");

    /** Copies everything. */
    ServerState
    {
        accounts = List.copyOf(accounts);
        superusers = List.copyOf(superusers);
        roles = Map.copyOf(roles);
        otherRoles = Map.copyOf(otherRoles);
        userPrivileges = Map.copyOf(userPrivileges);
        rowPolicies = Map.copyOf(rowPolicies);
    }

    /**
     * Names a row policy: its name and its table.
     *
     * @param name the policy's name; the agent's are named after their role
     * @param table the table
     */
    record RowPolicyKey(String name, TableRef table)
    {
    }

    /**
     * A row policy as Doris shows it.
     *
     * @param restrictive whether it is restrictive rather than permissive
     * @param predicate the condition, as Doris writes it back
     * @param user the identity it applies to, or {@code null}
     * @param role the role it applies to, or {@code null}
     */
    record RowPolicy(boolean restrictive, String predicate, @Nullable String user, @Nullable String role)
    {
    }

    /**
     * Reads the state.
     *
     * @param doris the cluster
     * @param prefix the start of the agent's roles' names
     * @param unmanaged the users the agent leaves alone
     * @return the state
     * @throws DorisFailure when the cluster fails a statement
     */
    // A value per row read is what reading the grants is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static ServerState read(Doris doris, String prefix, Set<String> unmanaged)
    {
        List<Row> mine = doris.query("SHOW GRANTS");
        String self = mine.isEmpty() ? "" : name(mine.get(0).string("UserIdentity"));

        Map<String, Set<Privilege>> roles = new HashMap<>();
        Map<String, Set<Privilege>> otherRoles = new HashMap<>();
        for (Row row : doris.query("SHOW ROLES")) {
            String role = row.string("Name");
            Set<Privilege> held = rolePrivileges(row);
            (role.startsWith(prefix) ? roles : otherRoles).put(role, held);
        }

        Map<String, List<String>> identities = new LinkedHashMap<>();
        Map<String, Map<String, Set<String>>> granted = new HashMap<>();
        Map<String, Map<String, Map<TableRef, Set<String>>>> columns = new HashMap<>();
        Map<String, Set<Privilege>> userPrivileges = new HashMap<>();
        Set<String> superusers = new LinkedHashSet<>();
        for (Row row : doris.query("SHOW ALL GRANTS")) {
            String identity = row.string("UserIdentity");
            String name = name(identity);
            if (name.isEmpty()) {
                continue;
            }
            String global = row.string("GlobalPrivs").toUpperCase(Locale.ROOT);
            if (global.contains("ADMIN_PRIV") || global.contains("NODE_PRIV")) {
                if (!name.equals(self) && !BUILT_IN_USERS.contains(name)) {
                    superusers.add(name);
                }
                continue;
            }
            if (name.equals(self) || unmanaged.contains(name) || BUILT_IN_USERS.contains(name)) {
                continue;
            }
            identities.computeIfAbsent(name, ignored -> new ArrayList<>()).add(identity);
            granted.computeIfAbsent(name, ignored -> new HashMap<>()).put(identity, Set.copyOf(names(row.string("Roles"))));
            columns.computeIfAbsent(name, ignored -> new HashMap<>()).put(identity, columnPrivileges(row.string("ColPrivs")));
            userPrivileges.put(identity, userPrivileges(row));
        }
        List<Account> accounts = new ArrayList<>();
        identities.forEach((name, ids) -> {
            Map<String, Set<String>> held = granted.getOrDefault(name, Map.of());
            Set<String> groups = new LinkedHashSet<>();
            held.values().forEach(names -> names.stream().filter(role -> !role.startsWith(prefix)).forEach(groups::add));
            accounts.add(new Account(name, ids, groups, held, columns.getOrDefault(name, Map.of())));
        });

        Map<RowPolicyKey, RowPolicy> rowPolicies = new LinkedHashMap<>();
        for (Row row : doris.query("SHOW ROW POLICY")) {
            String catalog = row.text("CatalogName");
            TableRef table = new TableRef(catalog == null || catalog.isEmpty() ? "internal" : catalog, row.string("DbName"), row.string("TableName"));
            rowPolicies.put(new RowPolicyKey(row.string("PolicyName"), table), new RowPolicy("RESTRICTIVE".equalsIgnoreCase(row.string("FilterType")),
                    row.string("WherePredicate"), blankToNull(row.text("User")), blankToNull(row.text("Role"))));
        }
        return new ServerState(self, accounts, List.copyOf(superusers), roles, otherRoles, userPrivileges, rowPolicies, inventory(doris));
    }

    /**
     * Reads the user name of an identity.
     *
     * @param identity such as {@code 'alice'@'%'}
     * @return such as {@code alice}, empty for text that is no identity
     */
    static String name(String identity)
    {
        Matcher matcher = IDENTITY.matcher(identity.strip());
        return matcher.matches() ? matcher.group(1).replace("\\'", "'").replace("\\\\", "\\") : "";
    }

    /** Names separated by commas, such as a user's roles. */
    static List<String> names(String text)
    {
        List<String> names = new ArrayList<>();
        for (String name : text.split(",")) {
            if (!name.isBlank() && !"NULL".equals(name.strip())) {
                names.add(name.strip());
            }
        }
        return names;
    }

    /** What a role holds, as {@code SHOW ROLES} shows it: the targets are written with their stars. */
    static Set<Privilege> rolePrivileges(Row row)
    {
        Set<Privilege> held = new LinkedHashSet<>();
        for (String column : List.of("CatalogPrivs", "DatabasePrivs", "TablePrivs")) {
            entries(row.string(column)).forEach((target, privileges) -> privileges.forEach(privilege -> held.add(new Privilege(privilege, target,
                    false))));
        }
        entries(row.string("WorkloadGroupPrivs")).forEach((group, privileges) -> privileges.forEach(privilege -> held.add(Privilege.workloadGroup(
                privilege, group))));
        return held;
    }

    /** What an identity may do through any grant, as {@code SHOW ALL GRANTS} shows it: the targets come without stars. */
    static Set<Privilege> userPrivileges(Row row)
    {
        Set<Privilege> held = new LinkedHashSet<>();
        entries(row.string("CatalogPrivs")).forEach((catalog, privileges) -> privileges.forEach(privilege -> held.add(Privilege.catalog(privilege,
                catalog))));
        entries(row.string("DatabasePrivs")).forEach((database, privileges) -> privileges.forEach(privilege -> held.add(new Privilege(privilege,
                database + ".*", false))));
        entries(row.string("TablePrivs")).forEach((table, privileges) -> privileges.forEach(privilege -> held.add(new Privilege(privilege, table,
                false))));
        entries(row.string("WorkloadGroupPrivs")).forEach((group, privileges) -> privileges.forEach(privilege -> held.add(Privilege.workloadGroup(
                privilege, group))));
        return held;
    }

    /**
     * Reads privileges by object as Doris shows them: {@code internal.sales.orders: Load_priv,Alter_priv; normal:
     * Usage_priv}.
     *
     * @param text the text, {@code NULL} or empty for none
     * @return the privileges of each object, in their order
     */
    // A list per object.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static Map<String, List<String>> entries(String text)
    {
        Map<String, List<String>> entries = new LinkedHashMap<>();
        if (text.isBlank() || "NULL".equals(text.strip())) {
            return entries;
        }
        for (String entry : text.split(";")) {
            int colon = entry.lastIndexOf(':');
            if (colon <= 0) {
                continue;
            }
            String target = entry.substring(0, colon).strip();
            List<String> privileges = entries.computeIfAbsent(target, ignored -> new ArrayList<>());
            privileges.addAll(names(entry.substring(colon + 1)));
        }
        return entries;
    }

    /**
     * Reads the column privileges {@code SHOW ALL GRANTS} shows: {@code internal.sales.orders: Select_priv[Region, id]}.
     *
     * @param text the text, {@code NULL} or empty for none
     * @return the readable columns of each table, as Doris shows them
     */
    // A table and a set per table.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static Map<TableRef, Set<String>> columnPrivileges(String text)
    {
        Map<TableRef, Set<String>> columns = new HashMap<>();
        if (text.isBlank() || "NULL".equals(text.strip())) {
            return columns;
        }
        for (String entry : text.split(";")) {
            int colon = entry.indexOf(": ");
            if (colon <= 0) {
                continue;
            }
            String[] parts = entry.substring(0, colon).strip().split("\\.", -1);
            Matcher privilege = COLUMNS.matcher(entry.substring(colon + 2).strip());
            if (parts.length != 3 || !privilege.matches() || !"SELECT_PRIV".equalsIgnoreCase(privilege.group(1))) {
                continue;
            }
            Set<String> names = columns.computeIfAbsent(new TableRef(parts[0], parts[1], parts[2]), ignored -> new LinkedHashSet<>());
            for (String column : privilege.group(2).split(",")) {
                if (!column.isBlank()) {
                    names.add(column.strip());
                }
            }
        }
        return columns;
    }

    private static @Nullable String blankToNull(@Nullable String text)
    {
        return text == null || text.isBlank() || "NULL".equals(text) ? null : text;
    }

    /** The catalogs, workload groups, and the levels below read when first asked for. */
    private static Inventory inventory(Doris doris)
    {
        List<String> catalogs = new ArrayList<>();
        catalogs.add("internal");
        for (Row row : doris.query("SHOW CATALOGS")) {
            String catalog = row.string("CatalogName");
            if (!catalog.isEmpty() && !catalogs.contains(catalog)) {
                catalogs.add(catalog);
            }
        }
        Set<String> groups = new LinkedHashSet<>();
        for (Row row : doris.query("SHOW WORKLOAD GROUPS")) {
            String group = row.string("Name");
            if (!group.isEmpty()) {
                groups.add(group);
            }
        }
        return new Inventory(catalogs, List.copyOf(groups),
                catalog -> first(doris, "SHOW DATABASES FROM " + Privilege.quote(catalog)).stream().filter(name -> !SYSTEM_DATABASES.contains(name))
                        .toList(),
                (catalog, database) -> first(doris, "SHOW TABLES FROM " + Privilege.quote(catalog) + "." + Privilege.quote(database)),
                table -> {
                    try {
                        return first(doris, "SHOW COLUMNS FROM " + table.sql());
                    }
                    catch (DorisFailure gone) {
                        if (gone.unreachable()) {
                            throw gone;
                        }
                        // A table dropped since its database was read has no columns.
                        return List.of();
                    }
                });
    }

    /** The first column of each row of a statement. */
    private static List<String> first(Doris doris, String sql)
    {
        List<String> values = new ArrayList<>();
        for (Row row : doris.query(sql)) {
            values.add(row.first());
        }
        return values;
    }
}
