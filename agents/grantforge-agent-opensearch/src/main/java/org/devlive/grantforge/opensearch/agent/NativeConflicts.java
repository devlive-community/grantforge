// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.policy.engine.AccessRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Warns, once each, of what roles outside GrantForge give its users that a GrantForge policy denies: OpenSearch grants
 * what any of a user's roles gives, so a role mapped to the user by name or by backend role, or given to an internal
 * user directly, decides beside the agent's. Users mapped to {@code all_access} are warned of as users no role binds.
 * The action groups and actions of a role's index permissions are read back into access types; those that name no
 * access type, such as a custom action group, are left out.
 */
final class NativeConflicts
{
    private static final Logger LOG = Logger.getLogger(NativeConflicts.class.getName());

    /** The access types that the security plugin's action groups and actions of indices give. */
    private static final Map<String, List<String>> GIVEN = given();

    private final Notices notices = new Notices(LOG);

    private static Map<String, List<String>> given()
    {
        List<String> everything = List.of(Planner.READ, "index", "delete", "create_index", "manage_aliases", "monitor", "manage");
        Map<String, List<String>> given = new LinkedHashMap<>();
        given.put("read", List.of(Planner.READ));
        given.put("search", List.of(Planner.READ));
        given.put("get", List.of(Planner.READ));
        given.put("index", List.of("index"));
        given.put("delete", List.of("delete"));
        given.put("write", List.of("index", "delete"));
        given.put("crud", List.of(Planner.READ, "index", "delete"));
        given.put("data_access", List.of(Planner.READ, "index", "delete"));
        given.put("create_index", List.of("create_index"));
        given.put("manage_aliases", List.of("manage_aliases"));
        given.put("indices_monitor", List.of("monitor"));
        given.put("manage", List.of("manage", "create_index", "manage_aliases", "monitor"));
        given.put("indices_all", everything);
        given.put("unlimited", everything);
        given.put("*", everything);
        given.put("indices:*", everything);
        return Map.copyOf(given);
    }

    /**
     * Returns the access types an action group or action gives.
     *
     * @param action the action group or action
     * @return the access types, none for what names none
     */
    static List<String> given(String action)
    {
        List<String> types = GIVEN.get(action);
        if (types != null) {
            return types;
        }
        String type = AuditLog.accessType(action.endsWith("*") ? action.substring(0, action.length() - 1) : action);
        return type == null || AuditLog.CLUSTER_TYPES.contains(type) ? List.of() : List.of(type);
    }

    /**
     * Checks the users the agent keeps roles for.
     *
     * @param state what the cluster holds
     * @param snapshot the policies
     * @param now the moment whose policies apply
     */
    // A set of access types per entry of a role.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    void check(ServerState state, Snapshot snapshot, Instant now)
    {
        for (String user : state.superusers()) {
            notices.once(Level.WARNING, "OpenSearch user " + user + " is mapped to all_access, which no role binds; GrantForge keeps no role for it");
        }
        Patterns patterns = new Patterns(snapshot, Planner.INDEX);
        for (Account account : state.accounts()) {
            for (String role : state.nativeRoles(account)) {
                JsonNode held = state.roles().get(role);
                if (held == null) {
                    continue;
                }
                for (JsonNode entry : held.path("index_permissions")) {
                    SortedSet<String> types = new TreeSet<>();
                    ServerState.texts(entry.path("allowed_actions")).forEach(action -> types.addAll(given(action)));
                    for (String pattern : ServerState.texts(entry.path("index_patterns"))) {
                        if (!Patterns.grantable(pattern)) {
                            continue;
                        }
                        for (String name : state.inventory().names()) {
                            if (patterns.matches(pattern, name)) {
                                conflicts(account, role, name, types, snapshot, now);
                            }
                        }
                    }
                }
            }
        }
    }

    private void conflicts(Account account, String role, String name, SortedSet<String> types, Snapshot snapshot, Instant now)
    {
        for (String type : types) {
            AgentDecision decision = snapshot.decide(AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0]))
                    .resource(Planner.INDEX, name).time(now).build());
            if (decision.outcome() == AgentDecision.Outcome.DENIED) {
                notices.once(Level.WARNING, "OpenSearch role " + role + ", which user " + account.name() + " has outside GrantForge, gives " + type + " on " + name
                        + ", which GrantForge policy " + decision.policyId() + " denies");
            }
        }
    }
}
