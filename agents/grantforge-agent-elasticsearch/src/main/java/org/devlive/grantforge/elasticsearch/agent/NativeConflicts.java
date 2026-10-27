// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

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
 * Warns, once each, of what roles outside GrantForge give its users that a GrantForge policy denies: Elasticsearch
 * grants what any of a user's roles gives, so a role a native user holds, or one a role mapping gives the user by name,
 * decides beside the agent's. Users with the {@code superuser} role are warned of as users no role binds. The privileges
 * of a role's index entries are read back into access types; a role mapping that maps by groups, realm or metadata is
 * not followed, as the agent cannot know whom it reaches.
 */
final class NativeConflicts
{
    private static final Logger LOG = Logger.getLogger(NativeConflicts.class.getName());

    /** The access types that Elasticsearch's index privileges give. */
    private static final Map<String, List<String>> GIVEN = given();

    private final Notices notices = new Notices(LOG);

    private static Map<String, List<String>> given()
    {
        List<String> everything = List.of(Planner.READ, "index", "delete", "create_index", "delete_index", "view_index_metadata", "monitor", "manage");
        Map<String, List<String>> given = new LinkedHashMap<>();
        given.put("read", List.of(Planner.READ));
        given.put("index", List.of("index"));
        given.put("create", List.of("index"));
        given.put("create_doc", List.of("index"));
        given.put("delete", List.of("delete"));
        given.put("write", List.of("index", "delete"));
        given.put("create_index", List.of("create_index"));
        given.put("auto_configure", List.of("create_index"));
        given.put("delete_index", List.of("delete_index"));
        given.put("view_index_metadata", List.of("view_index_metadata"));
        given.put("monitor", List.of("monitor"));
        given.put("manage", List.of("manage", "create_index", "delete_index", "view_index_metadata", "monitor"));
        given.put("all", everything);
        return Map.copyOf(given);
    }

    /**
     * Returns the access types an index privilege or action gives.
     *
     * @param action the privilege, such as {@code read}, or action, such as {@code indices:data/read/*}
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
            notices.once(Level.WARNING, "Elasticsearch user " + user + " has the superuser role, which no role binds; GrantForge keeps no role for it");
        }
        Patterns patterns = new Patterns(snapshot, Planner.INDEX);
        for (Account account : state.accounts()) {
            for (String role : state.nativeRoles(account)) {
                JsonNode held = state.roles().get(role);
                if (held == null) {
                    continue;
                }
                for (JsonNode entry : held.path("indices")) {
                    SortedSet<String> types = new TreeSet<>();
                    ServerState.texts(entry.path("privileges")).forEach(action -> types.addAll(given(action)));
                    for (String pattern : ServerState.texts(entry.path("names"))) {
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
                notices.once(Level.WARNING, "Elasticsearch role " + role + ", which user " + account.name() + " has outside GrantForge, gives " + type + " on " + name
                        + ", which GrantForge policy " + decision.policyId() + " denies");
            }
        }
    }
}
