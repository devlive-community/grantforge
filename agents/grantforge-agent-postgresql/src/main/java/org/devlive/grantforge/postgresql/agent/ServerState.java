// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What a PostgreSQL server holds at one moment that the agent looks at: the users it may keep roles for, the roles the
 * agent keeps, and for each database it keeps the schemas, tables and columns, the privileges of the agent's roles, the
 * row security of the tables and the privileges the agent did not give. Roles are the server's; schemas, tables and
 * their privileges are each database's own, and each database is read on its own connection.
 *
 * @param self the agent's own user
 * @param version the server's {@code server_version_num}, such as {@code 170011}
 * @param accounts the users the agent keeps roles for: those that sign in, but superusers, the agent itself and the
 *        users it leaves alone
 * @param superusers the users that sign in as superusers, whom privileges and row security do not bind
 * @param roles the agent's roles
 * @param databases the databases the agent keeps, by name, sorted
 * @param unreadable the databases the agent keeps but could not read, with why
 */
record ServerState(String self, int version, List<Account> accounts, List<String> superusers, Set<String> roles,
        Map<String, DatabaseState> databases, Map<String, String> unreadable)
{
    /** What the comment of each role the agent keeps starts with, so that a role of another's is never taken for one. */
    static final String MARK = "GrantForge agent role";

    /** The kinds of relations policies decide, and sequences. */
    static final String RELATION_KINDS = "('r', 'p', 'v', 'm', 'f')";
    /** Leaves out PostgreSQL's own schemas: those starting with pg_, which no user schema may, and information_schema. */
    static final String USER_SCHEMAS = "n.nspname <> 'information_schema' AND n.nspname NOT LIKE 'pg\\_%'";

    /** Copies everything. */
    ServerState
    {
        accounts = List.copyOf(accounts);
        superusers = List.copyOf(superusers);
        roles = Set.copyOf(roles);
        databases = Collections.unmodifiableSortedMap(new TreeMap<>(databases));
        unreadable = Map.copyOf(unreadable);
    }

    /**
     * What one database holds.
     *
     * @param name the database
     * @param inventory its schemas, tables and columns
     * @param held the privileges of each of the agent's roles in it, the database's own included
     * @param security the row security of each table that has it on or has policies
     * @param nativeGrants the privileges in it the agent did not give, of users, of other roles and of PUBLIC
     */
    record DatabaseState(String name, Inventory inventory, Map<String, Set<Privilege>> held, Map<TableRef, Security> security,
            List<NativeGrant> nativeGrants)
    {
        /** Copies everything. */
        DatabaseState
        {
            Map<String, Set<Privilege>> copy = new HashMap<>();
            held.forEach((role, privileges) -> copy.put(role, Set.copyOf(privileges)));
            held = Map.copyOf(copy);
            security = Map.copyOf(security);
            nativeGrants = List.copyOf(nativeGrants);
        }

        /**
         * Returns what one of the agent's roles holds in the database.
         *
         * @param role the role
         * @return the privileges, none for a role that holds none
         */
        Set<Privilege> held(String role)
        {
            return held.getOrDefault(role, Set.of());
        }
    }

    /**
     * The row security of a table.
     *
     * @param enabled whether PostgreSQL applies it
     * @param policies the table's row security policies, by name
     */
    record Security(boolean enabled, Map<String, RowPolicy> policies)
    {
        /** A table without row security. */
        static final Security NONE = new Security(false, Map.of());

        /** Copies the policies. */
        Security
        {
            policies = Map.copyOf(policies);
        }
    }

    /**
     * A row security policy as PostgreSQL shows it.
     *
     * @param permissive whether it is permissive rather than restrictive
     * @param command the commands it applies to: {@code r} SELECT, {@code *} all, and so on
     * @param roles whom it applies to, an empty name for PUBLIC
     * @param using its condition as PostgreSQL writes it back, or {@code null}
     */
    record RowPolicy(boolean permissive, String command, List<String> roles, @Nullable String using)
    {
        /** Copies the roles. */
        RowPolicy
        {
            roles = List.copyOf(roles);
        }

        /**
         * Returns whether the policy narrows what the one role reads, and nothing else.
         *
         * @param role the role
         * @return {@code true} for a restrictive policy for SELECT of that role alone
         */
        boolean filters(String role)
        {
            return !permissive && "r".equals(command) && roles.equals(List.of(role));
        }
    }

    /**
     * A privilege the agent did not give, or the ownership of a table.
     *
     * @param grantee the role that holds it, or {@code null} for PUBLIC
     * @param privilege the privilege; an ownership holds every privilege of the table and is named {@code OWNER}
     */
    record NativeGrant(@Nullable String grantee, Privilege privilege)
    {
    }

    /**
     * Reads the state.
     *
     * @param postgres the server
     * @param home the database the agent signs in to first
     * @param prefix the start of the agent's roles' names
     * @param unmanaged the users the agent leaves alone
     * @param wanted the databases the agent keeps, none for every database that allows connections
     * @return the state
     * @throws PostgreSqlFailure when the server fails a query of its roles or databases, or cannot be reached
     */
    // A value per row read is what reading the catalogs is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static ServerState read(PostgreSql postgres, String home, String prefix, Set<String> unmanaged, Set<String> wanted)
    {
        Row server = postgres.query(home, "SELECT current_user AS self, current_setting('server_version_num')::int AS version").get(0);
        String self = server.string("self");
        int version = (int) server.number("version");

        Map<String, Row> roles = new LinkedHashMap<>();
        for (Row row : postgres.query(home, "SELECT r.rolname AS name, r.rolcanlogin AS login, r.rolsuper AS superuser,"
                + " shobj_description(r.oid, 'pg_authid') AS comment FROM pg_roles r ORDER BY r.rolname")) {
            roles.put(row.string("name"), row);
        }
        Set<String> agentRoles = new LinkedHashSet<>();
        roles.forEach((name, row) -> {
            if (name.startsWith(prefix) && !row.flag("login") && row.string("comment").startsWith(MARK)) {
                agentRoles.add(name);
            }
        });

        Map<String, Set<String>> granted = new HashMap<>();
        Map<String, Set<String>> inheriting = new HashMap<>();
        String inherits = version >= 160_000 ? "am.inherit_option" : "m.rolinherit";
        for (Row row : postgres.query(home, "SELECT m.rolname AS member, g.rolname AS role, " + inherits + " AS inherit FROM pg_auth_members am"
                + " JOIN pg_roles m ON m.oid = am.member JOIN pg_roles g ON g.oid = am.roleid")) {
            granted.computeIfAbsent(row.string("member"), ignored -> new LinkedHashSet<>()).add(row.string("role"));
            if (row.flag("inherit")) {
                inheriting.computeIfAbsent(row.string("member"), ignored -> new LinkedHashSet<>()).add(row.string("role"));
            }
        }

        List<Account> accounts = new ArrayList<>();
        List<String> superusers = new ArrayList<>();
        roles.forEach((name, row) -> {
            if (!row.flag("login") || name.equals(self) || unmanaged.contains(name) || name.startsWith("pg_") || agentRoles.contains(name)) {
                return;
            }
            if (row.flag("superuser")) {
                superusers.add(name);
                return;
            }
            accounts.add(new Account(name, held(name, granted, agentRoles), granted.getOrDefault(name, Set.of()),
                    inheriting.getOrDefault(name, Set.of())));
        });

        List<String> names = new ArrayList<>();
        for (Row row : postgres.query(home, "SELECT datname AS name FROM pg_database WHERE datallowconn AND NOT datistemplate ORDER BY datname")) {
            if (wanted.isEmpty() || wanted.contains(row.string("name"))) {
                names.add(row.string("name"));
            }
        }
        Map<String, Map<String, Set<Privilege>>> held = new HashMap<>();
        Map<String, List<NativeGrant>> nativeGrants = new HashMap<>();
        for (Row row : postgres.query(home, "SELECT d.datname AS database, a.privilege_type AS privilege, g.rolname AS grantee,"
                + " a.grantor = d.datdba AS by_owner FROM pg_database d CROSS JOIN LATERAL aclexplode(COALESCE(d.datacl, acldefault('d', d.datdba))) a"
                + " LEFT JOIN pg_roles g ON g.oid = a.grantee WHERE d.datallowconn AND NOT d.datistemplate")) {
            sort(row, Privilege.database(row.string("database"), row.string("privilege")), agentRoles,
                    held.computeIfAbsent(row.string("database"), ignored -> new HashMap<>()),
                    nativeGrants.computeIfAbsent(row.string("database"), ignored -> new ArrayList<>()));
        }

        Map<String, DatabaseState> databases = new TreeMap<>();
        Map<String, String> unreadable = new TreeMap<>();
        for (String database : names) {
            try {
                databases.put(database, database(postgres, database, agentRoles, held.getOrDefault(database, new HashMap<>()),
                        nativeGrants.getOrDefault(database, new ArrayList<>())));
            }
            catch (PostgreSqlFailure failed) {
                if (failed.unreachable()) {
                    throw failed;
                }
                unreadable.put(database, String.valueOf(failed.getMessage()));
            }
        }
        return new ServerState(self, version, accounts, superusers, agentRoles, databases, unreadable);
    }

    /** Files a privilege under the agent's role that holds it, when the owner gave it, or under the privileges of others. */
    private static void sort(Row row, Privilege privilege, Set<String> agentRoles, Map<String, Set<Privilege>> held, List<NativeGrant> others)
    {
        String grantee = row.text("grantee");
        if (grantee != null && agentRoles.contains(grantee)) {
            // What another than the owner gave an agent's role, a superuser's REVOKE would not take away; it is left alone.
            if (row.flag("by_owner")) {
                held.computeIfAbsent(grantee, ignored -> new HashSet<>()).add(privilege);
            }
        }
        else {
            others.add(new NativeGrant(grantee, privilege));
        }
    }

    // A value per row read, as for read().
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static DatabaseState database(PostgreSql postgres, String database, Set<String> agentRoles, Map<String, Set<Privilege>> held,
            List<NativeGrant> nativeGrants)
    {
        Inventory inventory = Inventory.of(postgres.query(database, "SELECT n.nspname AS schema, c.relname AS name, c.relkind::text AS kind,"
                + " pg_get_userbyid(c.relowner) AS owner, a.attname AS column, s.relname AS sequence, sn.nspname AS sequence_schema"
                + " FROM pg_namespace n LEFT JOIN pg_class c ON c.relnamespace = n.oid AND c.relkind IN " + RELATION_KINDS
                + " LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped"
                + " LEFT JOIN (pg_depend d JOIN pg_class s ON s.oid = d.objid AND s.relkind = 'S' JOIN pg_namespace sn ON sn.oid = s.relnamespace)"
                + " ON d.classid = 'pg_class'::regclass AND d.refobjid = c.oid AND d.refobjsubid = a.attnum AND d.deptype = 'a'"
                + " WHERE " + USER_SCHEMAS + " ORDER BY n.nspname, c.relname, a.attnum"));

        for (Row row : postgres.query(database, "SELECT CASE WHEN c.relkind = 'S' THEN 'SEQUENCE' ELSE 'TABLE' END AS kind, n.nspname AS schema,"
                + " c.relname AS object, NULL::text AS column, a.privilege_type AS privilege, g.rolname AS grantee, a.grantor = c.relowner AS by_owner"
                + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace CROSS JOIN LATERAL aclexplode(c.relacl) a"
                + " LEFT JOIN pg_roles g ON g.oid = a.grantee WHERE c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S') AND " + USER_SCHEMAS
                + " UNION ALL SELECT 'COLUMN', n.nspname, c.relname, t.attname, a.privilege_type, g.rolname, a.grantor = c.relowner"
                + " FROM pg_attribute t JOIN pg_class c ON c.oid = t.attrelid JOIN pg_namespace n ON n.oid = c.relnamespace"
                + " CROSS JOIN LATERAL aclexplode(t.attacl) a LEFT JOIN pg_roles g ON g.oid = a.grantee"
                + " WHERE t.attnum > 0 AND NOT t.attisdropped AND c.relkind IN " + RELATION_KINDS + " AND " + USER_SCHEMAS
                + " UNION ALL SELECT 'SCHEMA', NULL, n.nspname, NULL, a.privilege_type, g.rolname, a.grantor = n.nspowner"
                + " FROM pg_namespace n CROSS JOIN LATERAL aclexplode(n.nspacl) a LEFT JOIN pg_roles g ON g.oid = a.grantee WHERE " + USER_SCHEMAS)) {
            Privilege.Kind kind = Privilege.Kind.valueOf(row.string("kind"));
            Privilege privilege = new Privilege(kind, row.text("schema"), row.string("object"), row.text("column"), row.string("privilege"));
            sort(row, privilege, agentRoles, held, nativeGrants);
        }
        for (String schema : inventory.schemas()) {
            for (Inventory.Table table : inventory.tables(schema)) {
                nativeGrants.add(new NativeGrant(table.owner(), Privilege.table(table.ref(), "OWNER")));
            }
        }

        Map<TableRef, Boolean> enabled = new HashMap<>();
        Map<TableRef, Map<String, RowPolicy>> policies = new HashMap<>();
        for (Row row : postgres.query(database, "SELECT n.nspname AS schema, c.relname AS table, c.relrowsecurity AS enabled, p.polname AS name,"
                + " p.polpermissive AS permissive, p.polcmd::text AS command, ARRAY(SELECT COALESCE(r.rolname, '') FROM unnest(p.polroles) AS o"
                + " LEFT JOIN pg_roles r ON r.oid = o) AS roles, pg_get_expr(p.polqual, p.polrelid) AS using FROM pg_class c"
                + " JOIN pg_namespace n ON n.oid = c.relnamespace LEFT JOIN pg_policy p ON p.polrelid = c.oid"
                + " WHERE (c.relrowsecurity OR p.oid IS NOT NULL) AND " + USER_SCHEMAS)) {
            TableRef table = new TableRef(row.string("schema"), row.string("table"));
            enabled.put(table, row.flag("enabled"));
            Map<String, RowPolicy> those = policies.computeIfAbsent(table, ignored -> new HashMap<>());
            String name = row.text("name");
            if (name != null) {
                those.put(name, new RowPolicy(row.flag("permissive"), row.string("command"), row.texts("roles"), row.text("using")));
            }
        }
        Map<TableRef, Security> security = new HashMap<>();
        enabled.forEach((table, on) -> security.put(table, new Security(on, policies.getOrDefault(table, Map.of()))));
        return new DatabaseState(database, inventory, held, security, nativeGrants);
    }

    /** The roles a user is a member of, directly or through other roles, the agent's own left out. */
    private static Set<String> held(String user, Map<String, Set<String>> granted, Set<String> agentRoles)
    {
        Set<String> held = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(granted.getOrDefault(user, Set.of()));
        while (!pending.isEmpty()) {
            String role = pending.pop();
            if (!agentRoles.contains(role) && held.add(role)) {
                pending.addAll(granted.getOrDefault(role, Set.of()));
            }
        }
        return held;
    }
}
