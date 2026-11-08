// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * What a deployment holds: its users with the roles they hold, the agent's roles with their privileges, what every other
 * role a user holds gives, built-in ones too, whether authorization is on at all, and what policies can name.
 *
 * @param accounts the users the agent keeps roles for
 * @param superusers the users no role binds, which the agent leaves alone, with the role that makes them so
 * @param managed the agent's roles, by name
 * @param others what the other roles users hold give, by role
 * @param inventory the deployment, its databases and collections
 * @param authorization whether the server checks authorization, or {@code null} when it does not say
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
record ServerState(List<Account> accounts, Map<String, Account.RoleRef> superusers, Map<String, ManagedRole> managed, Map<Account.RoleRef, Role> others, Inventory inventory,
        @Nullable Boolean authorization)
{
    /** The built-in roles that no role binds; users who hold them are left alone. */
    private static final Set<String> SUPERUSER_ROLES = Set.of("root", "__system");

    /** The database of the agent's roles, as a role there can hold privileges on every database. */
    static final String ROLE_DATABASE = "admin";

    /**
     * A role of the agent.
     *
     * @param privileges what it holds
     * @param inherited whether it inherits other roles, which the agent's never do unless someone changed them by hand
     * @param comparable whether every privilege it holds is one the agent would write
     */
    record ManagedRole(Plan privileges, boolean inherited, boolean comparable)
    {
    }

    /**
     * What a role outside GrantForge gives, inherited roles included.
     *
     * @param privileges the actions by target
     * @param everything whether it holds every action on every resource
     */
    record Role(Map<Target, Set<String>> privileges, boolean everything)
    {
        /** Copies the privileges. */
        Role
        {
            privileges = Map.copyOf(privileges);
        }

        /**
         * Returns whether the role lets its users change who may do what.
         *
         * @return whether it does
         */
        boolean administersUsers()
        {
            return everything || privileges.values().stream().anyMatch(actions -> actions.stream().anyMatch(Actions.USER_ADMINISTRATION::contains));
        }
    }

    /** Copies everything. */
    ServerState
    {
        accounts = List.copyOf(accounts);
        superusers = Map.copyOf(superusers);
        managed = Map.copyOf(managed);
        others = Map.copyOf(others);
    }

    /**
     * Reads what the deployment holds.
     *
     * @param mongo the deployment
     * @param rolePrefix what the names of the agent's roles start with
     * @param unmanaged the users to leave alone by name
     * @return what it holds
     * @throws MongoFailure when the deployment fails a command
     */
    // A list and set per user and role is what reading them is.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static ServerState read(MongoDb mongo, String rolePrefix, Set<String> unmanaged)
    {
        Map<String, ManagedRole> managed = managedRoles(mongo, rolePrefix);
        Map<String, List<Account.Identity>> byName = new LinkedHashMap<>();
        for (Document user : list(mongo.command(ROLE_DATABASE, new Document("usersInfo", new Document("forAllDBs", true))), "users")) {
            List<Account.RoleRef> roles = new ArrayList<>();
            for (Document role : list(user, "roles")) {
                roles.add(new Account.RoleRef(String.valueOf(role.getString("role")), String.valueOf(role.getString("db"))));
            }
            String name = String.valueOf(user.getString("user"));
            byName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(new Account.Identity(name, String.valueOf(user.getString("db")), roles));
        }
        Predicate<Account.RoleRef> ours = role -> ROLE_DATABASE.equals(role.database()) && role.role().startsWith(rolePrefix);
        Set<Account.RoleRef> held = new LinkedHashSet<>();
        byName.values().forEach(identities -> identities.forEach(identity -> identity.roles().stream().filter(ours.negate()).forEach(held::add)));
        Map<Account.RoleRef, Role> others = held.isEmpty() ? Map.of() : others(mongo, held);
        List<Account> accounts = new ArrayList<>();
        Map<String, Account.RoleRef> superusers = new HashMap<>();
        byName.forEach((name, identities) -> {
            Set<Account.RoleRef> roles = new LinkedHashSet<>();
            Set<String> groups = new TreeSet<>();
            for (Account.Identity identity : identities) {
                identity.roles().stream().filter(ours.negate()).forEach(role -> {
                    roles.add(role);
                    groups.add(role.role());
                });
            }
            Account.RoleRef unbound = unbound(roles, others);
            if (unbound != null) {
                superusers.put(name, unbound);
            }
            else if (!unmanaged.contains(name)) {
                accounts.add(new Account(name, identities, groups, roles));
            }
        });
        Document hello = mongo.command(ROLE_DATABASE, new Document("hello", 1));
        Inventory inventory = new Inventory(deployment(hello), mongo.databases(), mongo::collections);
        return new ServerState(accounts, superusers, managed, others, inventory, authorization(mongo));
    }

    /** The role that no role binds among those a user holds, or {@code null} when it holds none. */
    private static Account.@Nullable RoleRef unbound(Set<Account.RoleRef> roles, Map<Account.RoleRef, Role> others)
    {
        for (Account.RoleRef role : roles) {
            Role held = others.get(role);
            if (SUPERUSER_ROLES.contains(role.role()) || held != null && held.everything()) {
                return role;
            }
        }
        return null;
    }

    /** The roles of the agent, read back with what they hold. */
    // A set and a target per privilege.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, ManagedRole> managedRoles(MongoDb mongo, String rolePrefix)
    {
        Map<String, ManagedRole> managed = new HashMap<>();
        for (Document role : list(mongo.command(ROLE_DATABASE, new Document("rolesInfo", 1).append("showPrivileges", true)), "roles")) {
            String name = String.valueOf(role.getString("role"));
            if (!name.startsWith(rolePrefix)) {
                continue;
            }
            SortedMap<Target, SortedSet<String>> privileges = new TreeMap<>(Target.ORDER);
            boolean comparable = true;
            for (Document privilege : list(role, "privileges")) {
                Document resource = privilege.get("resource", Document.class);
                Target target = resource == null ? null : Target.of(resource);
                if (target == null) {
                    comparable = false;
                }
                else {
                    privileges.computeIfAbsent(target, ignored -> new TreeSet<>()).addAll(privilege.getList("actions", String.class));
                }
            }
            managed.put(name, new ManagedRole(new Plan(privileges), !list(role, "roles").isEmpty(), comparable));
        }
        return managed;
    }

    /** What the other roles give, MongoDB resolving what they inherit and what the built-in ones hold. */
    // A map and a set per role and privilege.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<Account.RoleRef, Role> others(MongoDb mongo, Set<Account.RoleRef> held)
    {
        List<Document> asked = held.stream().map(role -> new Document("role", role.role()).append("db", role.database())).toList();
        Map<Account.RoleRef, Role> others = new HashMap<>();
        for (Document role : list(mongo.command(ROLE_DATABASE, new Document("rolesInfo", asked).append("showPrivileges", true).append("showBuiltinRoles", true)), "roles")) {
            Map<Target, Set<String>> privileges = new HashMap<>();
            boolean everything = false;
            for (Document privilege : list(role, "inheritedPrivileges")) {
                Document resource = privilege.get("resource", Document.class);
                List<String> actions = privilege.getList("actions", String.class);
                if (resource != null && Boolean.TRUE.equals(resource.getBoolean("anyResource")) && actions.contains("anyAction")) {
                    everything = true;
                }
                Target target = resource == null ? null : Target.of(resource);
                if (target != null) {
                    privileges.computeIfAbsent(target, ignored -> new TreeSet<>()).addAll(actions);
                }
            }
            others.put(new Account.RoleRef(String.valueOf(role.getString("role")), String.valueOf(role.getString("db"))), new Role(privileges, everything));
        }
        return others;
    }

    /**
     * Names a deployment as policies name it.
     *
     * @param hello the answer of the {@code hello} command
     * @return the replica set's name, {@link Inventory#SHARDED} for a {@code mongos}, otherwise {@link Inventory#STANDALONE}
     */
    static String deployment(Document hello)
    {
        String set = hello.getString("setName");
        if (set != null) {
            return set;
        }
        return "isdbgrid".equals(hello.getString("msg")) ? Inventory.SHARDED : Inventory.STANDALONE;
    }

    /**
     * Says whether the server the agent talks to checks authorization. A key file or cluster authentication turns it on as
     * surely as the setting itself.
     *
     * @return whether it does, or {@code null} when the agent's user may not ask
     * @throws MongoFailure when the deployment cannot be reached
     */
    private static @Nullable Boolean authorization(MongoDb mongo)
    {
        Document options;
        try {
            options = mongo.command(ROLE_DATABASE, new Document("getCmdLineOpts", 1));
        }
        catch (MongoFailure refused) {
            if (refused.unreachable()) {
                throw refused;
            }
            return null;
        }
        Document parsed = options.get("parsed", Document.class);
        Document security = parsed == null ? null : parsed.get("security", Document.class);
        return security != null && ("enabled".equals(security.getString("authorization")) || security.get("keyFile") != null
                || security.get("clusterAuthMode") != null);
    }

    /** The documents of an array of a document, none when it has none. */
    private static List<Document> list(Document document, String key)
    {
        List<Document> list = document.getList(key, Document.class);
        return list == null ? List.of() : list;
    }
}
