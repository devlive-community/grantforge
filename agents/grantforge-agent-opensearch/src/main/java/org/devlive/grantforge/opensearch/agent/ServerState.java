// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What the cluster holds that the agent decides on: its release and name, the agent's own user, the security plugin's
 * internal users, roles and role mappings, the agent's own roles among them, the users it keeps roles for, those mapped
 * to {@code all_access}, whom no role binds, and the indices and data streams.
 */
final class ServerState
{
    /** How the description of a role the agent keeps starts; the user's name follows. */
    static final String MARKER = "GrantForge agent role of user ";
    /** The security plugin's role that allows everything. */
    static final String ALL_ACCESS = "all_access";

    private static final String API = "/_plugins/_security/api/";

    /**
     * An internal user of the security plugin.
     *
     * @param name the name
     * @param backendRoles its backend roles, which role mappings and policies name
     * @param securityRoles the roles it is given directly
     * @param reserved whether the security plugin reserves or hides it, as it does its own users such as kibanaserver
     */
    record InternalUser(String name, Set<String> backendRoles, Set<String> securityRoles, boolean reserved)
    {
        /** Copies the roles. */
        InternalUser
        {
            backendRoles = Set.copyOf(backendRoles);
            securityRoles = Set.copyOf(securityRoles);
        }
    }

    private final String version;
    private final String self;
    private final Map<String, InternalUser> internalUsers;
    private final Map<String, JsonNode> roles;
    private final Map<String, JsonNode> mappings;
    private final Map<String, String> agentRoles;
    private final List<Account> accounts;
    private final SortedSet<String> superusers;
    private final Inventory inventory;

    ServerState(String version, String self, Map<String, InternalUser> internalUsers, Map<String, JsonNode> roles, Map<String, JsonNode> mappings,
            Map<String, String> agentRoles, List<Account> accounts, SortedSet<String> superusers, Inventory inventory)
    {
        this.version = version;
        this.self = self;
        this.internalUsers = Collections.unmodifiableMap(new TreeMap<>(internalUsers));
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
     * @param named the users the policies name, who may sign in through a directory the agent cannot list
     * @return the state
     * @throws OpenSearchFailure when OpenSearch refuses or fails a request, or cannot be reached
     */
    // An account per user.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static ServerState read(OpenSearch search, String prefix, Set<String> unmanaged, Set<String> named)
    {
        JsonNode root = search.get("/", Map.of());
        String self = search.get("/_plugins/_security/authinfo", Map.of()).path("user_name").asText("");
        Map<String, InternalUser> internal = new LinkedHashMap<>();
        fields(search.get(API + "internalusers", Map.of())).forEach((name, user) -> internal.put(name, new InternalUser(name,
                texts(user.path("backend_roles")), texts(user.path("opendistro_security_roles")),
                user.path("reserved").asBoolean(false) || user.path("hidden").asBoolean(false))));
        Map<String, JsonNode> roles = fields(search.get(API + "roles", Map.of()));
        Map<String, JsonNode> mappings = fields(search.get(API + "rolesmapping", Map.of()));
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
        internal.values().stream().filter(user -> !user.reserved()).forEach(user -> users.add(user.name()));
        users.addAll(agentRoles.values());
        users.removeAll(unmanaged);
        users.remove(self);
        List<Account> accounts = new ArrayList<>();
        SortedSet<String> superusers = new TreeSet<>();
        ServerState partial = new ServerState(root.path("version").path("number").asText(""), self, internal, roles, mappings, agentRoles, List.of(),
                superusers, inventory);
        for (String user : users) {
            InternalUser known = internal.get(user);
            Account account = new Account(user, known == null ? Set.of() : known.backendRoles(), known != null);
            if (partial.nativeRoles(account).contains(ALL_ACCESS)) {
                superusers.add(user);
            }
            else {
                accounts.add(account);
            }
        }
        return new ServerState(partial.version, self, internal, roles, mappings, agentRoles, accounts, superusers, inventory);
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
        String description = role.path("description").asText("");
        if (!name.startsWith(prefix) || !description.startsWith(MARKER) || role.path("reserved").asBoolean(false)) {
            return null;
        }
        String user = description.substring(MARKER.length());
        return name.equals(prefix + user) ? user : null;
    }

    /**
     * Returns the roles outside the agent's that a user has: those whose mappings name the user or one of its backend
     * roles, and those an internal user is given directly.
     *
     * @param account the user
     * @return the roles' names, sorted
     */
    SortedSet<String> nativeRoles(Account account)
    {
        SortedSet<String> found = new TreeSet<>();
        InternalUser known = internalUsers.get(account.name());
        if (known != null) {
            found.addAll(known.securityRoles());
        }
        mappings.forEach((role, mapping) -> {
            if (agentRoles.containsKey(role)) {
                return;
            }
            boolean user = texts(mapping.path("users")).stream().anyMatch(pattern -> Patterns.glob(pattern).run(account.name()));
            boolean backend = texts(mapping.path("backend_roles")).stream().anyMatch(pattern -> "*".equals(pattern) || account.groups().contains(pattern));
            if (user || backend) {
                found.add(role);
            }
        });
        found.removeAll(agentRoles.keySet());
        return found;
    }

    private static Map<String, JsonNode> fields(JsonNode object)
    {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = object.fields();
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

    /** The cluster's release, such as {@code 3.9.0}. */
    String version()
    {
        return version;
    }

    /** The agent's own user. */
    String self()
    {
        return self;
    }

    /** The security plugin's internal users, by name. */
    Map<String, InternalUser> internalUsers()
    {
        return internalUsers;
    }

    /** Every role, by name. */
    Map<String, JsonNode> roles()
    {
        return roles;
    }

    /** Every role mapping, by role. */
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

    /** The users mapped to {@code all_access}, whom the agent keeps no role for. */
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
