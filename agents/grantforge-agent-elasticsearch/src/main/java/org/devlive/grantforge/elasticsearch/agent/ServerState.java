// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the cluster holds that the agent decides on: its release and name, its license, the agent's own user, the
 * native users, roles and role mappings, the agent's own roles among them, the users it keeps roles for, those with the
 * {@code superuser} role, whom no role binds, and the indices and data streams.
 */
final class ServerState
{
    /** How the description of a role the agent keeps starts; the user's name follows. */
    static final String MARKER = "GrantForge agent role of user ";
    /** The metadata key that names the user a role or role mapping of the agent is for. */
    static final String OWNER = "grantforge_user";
    /** Elasticsearch's built-in role that allows everything. */
    static final String SUPERUSER = "superuser";

    /** The licenses with document and field level security, as Elasticsearch decides: Platinum and above. */
    static final Set<String> DLS_FLS_LICENSES = Set.of("platinum", "enterprise", "trial");
    /** The licenses with audit logging, as Elasticsearch decides: Gold and above. */
    static final Set<String> AUDIT_LICENSES = Set.of("gold", "platinum", "enterprise", "trial");

    /**
     * A user of Elasticsearch's native realm, which holds its roles itself.
     *
     * @param name the name
     * @param roles the roles it is given
     * @param body what the user API returned, written back with the roles changed
     * @param reserved whether Elasticsearch reserves it, as it does its own users such as kibana_system
     */
    record NativeUser(String name, SortedSet<String> roles, JsonNode body, boolean reserved)
    {
        /** Copies the roles. */
        NativeUser
        {
            roles = Collections.unmodifiableSortedSet(new TreeSet<>(roles));
        }
    }

    /**
     * The cluster's license.
     *
     * @param type such as basic, gold, platinum, enterprise or trial
     * @param active whether it is in force
     */
    record License(String type, boolean active)
    {
        /** Whether roles may restrict fields and documents; below Platinum Elasticsearch turns such roles off. */
        boolean restricting()
        {
            return active && DLS_FLS_LICENSES.contains(type);
        }

        /** Whether Elasticsearch writes its audit log; it does from Gold on. */
        boolean auditing()
        {
            return active && AUDIT_LICENSES.contains(type);
        }
    }

    private final String version;
    private final String self;
    private final License license;
    private final Map<String, NativeUser> nativeUsers;
    private final Map<String, JsonNode> roles;
    private final Map<String, JsonNode> mappings;
    private final Map<String, String> agentRoles;
    private final List<Account> accounts;
    private final SortedSet<String> superusers;
    private final Inventory inventory;

    ServerState(String version, String self, License license, Map<String, NativeUser> nativeUsers, Map<String, JsonNode> roles,
            Map<String, JsonNode> mappings, Map<String, String> agentRoles, List<Account> accounts, SortedSet<String> superusers, Inventory inventory)
    {
        this.version = version;
        this.self = self;
        this.license = license;
        this.nativeUsers = Collections.unmodifiableMap(new TreeMap<>(nativeUsers));
        this.roles = Collections.unmodifiableMap(new TreeMap<>(roles));
        this.mappings = Collections.unmodifiableMap(new TreeMap<>(mappings));
        this.agentRoles = Collections.unmodifiableMap(new TreeMap<>(agentRoles));
        this.accounts = List.copyOf(accounts);
        this.superusers = Collections.unmodifiableSortedSet(new TreeSet<>(superusers));
        this.inventory = inventory;
    }

    /**
     * Reads what the cluster holds.
     *
     * @param search the cluster
     * @param prefix the start of the names of the agent's roles
     * @param unmanaged the users the agent leaves alone
     * @param named the users the policies name, who may sign in through a realm the agent cannot list
     * @return the state
     * @throws ElasticsearchFailure when Elasticsearch refuses or fails a request, or cannot be reached
     */
    // An account per user.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static ServerState read(Elasticsearch search, String prefix, Set<String> unmanaged, Set<String> named)
    {
        JsonNode root = search.get("/", Map.of());
        String self = search.get("/_security/_authenticate", Map.of()).path("username").asText("");
        JsonNode held = search.get("/_license", Map.of()).path("license");
        License license = new License(held.path("type").asText("").toLowerCase(Locale.ROOT), "active".equals(held.path("status").asText("")));
        Map<String, NativeUser> nativeUsers = new LinkedHashMap<>();
        fields(search.get("/_security/user", Map.of())).forEach((name, user) -> nativeUsers.put(name, new NativeUser(name, texts(user.path("roles")), user,
                user.path("metadata").path("_reserved").asBoolean(false))));
        Map<String, JsonNode> roles = fields(search.get("/_security/role", Map.of()));
        Map<String, JsonNode> mappings = fields(search.get("/_security/role_mapping", Map.of()));
        Map<String, String> agentRoles = new LinkedHashMap<>();
        roles.forEach((name, role) -> {
            String user = owner(name, role, prefix);
            if (user != null) {
                agentRoles.put(name, user);
            }
        });
        Inventory inventory = Inventory.of(root.path("cluster_name").asText(""),
                search.get("/_resolve/index/*", Map.of("expand_wildcards", "open,closed")),
                target -> search.get("/" + target + "/_mapping", Map.of("expand_wildcards", "open,closed", "ignore_unavailable", "true",
                        "allow_no_indices", "true")));
        SortedSet<String> users = new TreeSet<>(named);
        nativeUsers.values().stream().filter(user -> !user.reserved()).forEach(user -> users.add(user.name()));
        users.addAll(agentRoles.values());
        users.removeAll(unmanaged);
        users.remove(self);
        nativeUsers.values().stream().filter(NativeUser::reserved).forEach(user -> users.remove(user.name()));
        List<Account> accounts = new ArrayList<>();
        SortedSet<String> superusers = new TreeSet<>();
        ServerState partial = new ServerState(root.path("version").path("number").asText(""), self, license, nativeUsers, roles, mappings, agentRoles,
                List.of(), superusers, inventory);
        for (String user : users) {
            NativeUser known = nativeUsers.get(user);
            Account account = new Account(user, known == null ? Set.of() : partial.groups(known), known != null);
            if (partial.nativeRoles(account).contains(SUPERUSER)) {
                superusers.add(user);
            }
            else {
                accounts.add(account);
            }
        }
        return new ServerState(partial.version, self, license, nativeUsers, roles, mappings, agentRoles, accounts, superusers, inventory);
    }

    /** A native user's roles outside the agent's, which policies name as groups. */
    private Set<String> groups(NativeUser user)
    {
        Set<String> groups = new TreeSet<>(user.roles());
        groups.removeAll(agentRoles.keySet());
        return groups;
    }

    /**
     * Names the user a role is the agent's for.
     *
     * @param name the role's name
     * @param role the role
     * @param prefix the start of the names of the agent's roles
     * @return the user, or {@code null} for a role the agent does not keep
     */
    static @Nullable String owner(String name, JsonNode role, String prefix)
    {
        JsonNode metadata = role.path("metadata");
        String user = metadata.path(OWNER).asText("");
        if (!name.startsWith(prefix) || user.isEmpty() || metadata.path("_reserved").asBoolean(false)) {
            return null;
        }
        return name.equals(prefix + user) ? user : null;
    }

    /**
     * Returns the roles outside the agent's that a user has: those a native user holds, and those of role mappings
     * whose rules name the user.
     *
     * @param account the user
     * @return the roles' names, sorted
     */
    SortedSet<String> nativeRoles(Account account)
    {
        SortedSet<String> found = new TreeSet<>();
        NativeUser known = nativeUsers.get(account.name());
        if (known != null) {
            found.addAll(known.roles());
        }
        mappings.forEach((name, mapping) -> {
            if (agentRoles.containsKey(name) || !mapping.path("enabled").asBoolean(true)) {
                return;
            }
            if (matches(mapping.path("rules"), account.name())) {
                found.addAll(texts(mapping.path("roles")));
            }
        });
        found.removeAll(agentRoles.keySet());
        return found;
    }

    /**
     * Tells whether role mapping rules are sure to map a user by name alone: a {@code username} field rule, or
     * {@code any}, {@code all} and {@code except} of such rules. A rule on anything else, such as groups or the realm,
     * is taken not to apply, as the agent cannot know it.
     *
     * @param rules the rules
     * @param user the user's name
     * @return whether they map the user
     */
    static boolean matches(JsonNode rules, String user)
    {
        if (rules.has("any")) {
            for (JsonNode rule : rules.path("any")) {
                if (matches(rule, user)) {
                    return true;
                }
            }
            return false;
        }
        if (rules.has("all")) {
            if (!rules.path("all").isArray() || rules.path("all").isEmpty()) {
                return false;
            }
            for (JsonNode rule : rules.path("all")) {
                if (!matches(rule, user)) {
                    return false;
                }
            }
            return true;
        }
        if (rules.has("except")) {
            return false;
        }
        JsonNode name = rules.path("field").path("username");
        if (name.isMissingNode()) {
            return false;
        }
        for (JsonNode value : name.isArray() ? name : List.of(name)) {
            if (value.isTextual() && Patterns.glob(value.asText()).run(user)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, JsonNode> fields(JsonNode object)
    {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = object.properties().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            fields.put(entry.getKey(), entry.getValue());
        }
        return fields;
    }

    /**
     * Reads a JSON array of texts.
     *
     * @param array the array
     * @return its texts, sorted; none for what is no array
     */
    static SortedSet<String> texts(JsonNode array)
    {
        SortedSet<String> texts = new TreeSet<>();
        for (JsonNode text : array) {
            if (text.isTextual()) {
                texts.add(text.asText());
            }
        }
        return texts;
    }

    /** The cluster's release, such as {@code 9.5.5}. */
    String version()
    {
        return version;
    }

    /** The agent's own user. */
    String self()
    {
        return self;
    }

    /** The cluster's license. */
    License license()
    {
        return license;
    }

    /** The native users, by name. */
    Map<String, NativeUser> nativeUsers()
    {
        return nativeUsers;
    }

    /** Every role the role API lists, by name. */
    Map<String, JsonNode> roles()
    {
        return roles;
    }

    /** Every role mapping, by name. */
    Map<String, JsonNode> mappings()
    {
        return mappings;
    }

    /** The agent's roles, with the user each is for. */
    Map<String, String> agentRoles()
    {
        return agentRoles;
    }

    /** The users the agent keeps roles for. */
    List<Account> accounts()
    {
        return accounts;
    }

    /** The users with the {@code superuser} role, whom the agent keeps no role for. */
    SortedSet<String> superusers()
    {
        return superusers;
    }

    /** The indices and data streams. */
    Inventory inventory()
    {
        return inventory;
    }
}
