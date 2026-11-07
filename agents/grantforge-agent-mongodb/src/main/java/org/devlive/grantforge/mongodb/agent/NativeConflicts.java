// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Warns, once each, of what the agent cannot take away: MongoDB adds up what all of a user's roles give, so a built-in or
 * custom role a user holds outside GrantForge decides beside the agent's. MongoDB itself says what every role gives,
 * built-in ones and inherited ones included, so that holding {@code readWrite} on a database is checked like any
 * other role. Also warned of are a server that checks no authorization at all, users no role binds, and users who may
 * administer users, and could so give themselves anything.
 */
final class NativeConflicts
{
    private static final Logger LOG = Logger.getLogger(NativeConflicts.class.getName());

    private final Notices notices = new Notices(LOG);

    /**
     * Checks the deployment.
     *
     * @param state what the deployment holds
     * @param snapshot the policies
     * @param now the moment whose policies apply
     */
    void check(ServerState state, Snapshot snapshot, Instant now)
    {
        if (Boolean.FALSE.equals(state.authorization())) {
            notices.once(Level.WARNING, "MongoDB checks no authorization: every client may do anything, so GrantForge's policies decide nothing until it runs with"
                    + " security.authorization enabled");
        }
        state.superusers().forEach((user, role) -> notices.once(Level.WARNING, "MongoDB user " + user + " has the role " + role.label()
                + ", which no role binds; GrantForge keeps no role for it"));
        for (Account account : state.accounts()) {
            for (Account.RoleRef role : account.roles()) {
                ServerState.Role held = state.others().get(role);
                if (held != null) {
                    check(account, role, held, state.inventory(), snapshot, now);
                }
            }
        }
    }

    // A set of access types per privilege of a role.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private void check(Account account, Account.RoleRef role, ServerState.Role held, Inventory inventory, Snapshot snapshot, Instant now)
    {
        if (held.administersUsers()) {
            notices.once(Level.WARNING, "MongoDB user " + account.name() + " has the role " + role.label() + " outside GrantForge, which lets it administer users and so give"
                    + " itself anything a policy denies");
        }
        for (Map.Entry<Target, Set<String>> privilege : held.privileges().entrySet()) {
            Target target = privilege.getKey();
            SortedSet<String> types = new TreeSet<>();
            privilege.getValue().forEach(action -> {
                String type = Actions.type(action);
                if (type != null) {
                    types.add(type);
                }
            });
            if (target.database() == null) {
                types.stream().filter(Actions.DEPLOYMENT_TYPES::contains).forEach(type -> conflict(account, role, type, Target.DEPLOYMENT, deployment(account, type, inventory, now),
                        snapshot));
                continue;
            }
            for (String database : inventory.databases()) {
                check(account, role, target, types, database, inventory, snapshot, now);
            }
        }
    }

    private void check(Account account, Account.RoleRef role, Target target, SortedSet<String> types, String database, Inventory inventory, Snapshot snapshot, Instant now)
    {
        if (target.coversAll(database)) {
            for (String type : types) {
                if (!Actions.COLLECTION_TYPES.contains(type)) {
                    conflict(account, role, type, Target.database(database), request(account, type, database, null, now), snapshot);
                }
            }
        }
        for (String collection : inventory.collections(database)) {
            if (target.covers(database, collection)) {
                for (String type : types) {
                    if (Actions.COLLECTION_TYPES.contains(type)) {
                        conflict(account, role, type, Target.collection(database, collection), request(account, type, database, collection, now), snapshot);
                    }
                }
            }
        }
    }

    private void conflict(Account account, Account.RoleRef role, String type, Target on, AccessRequest request, Snapshot snapshot)
    {
        AgentDecision decision = snapshot.decide(request);
        if (decision.outcome() == AgentDecision.Outcome.DENIED) {
            notices.once(Level.WARNING, "MongoDB role " + role.label() + ", which user " + account.name() + " has outside GrantForge, gives " + type + " on " + on.label()
                    + ", which GrantForge policy " + decision.policyId() + " denies");
        }
    }

    private static AccessRequest deployment(Account account, String type, Inventory inventory, Instant now)
    {
        return AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0])).resource(Planner.DEPLOYMENT, inventory.deployment()).time(now).build();
    }

    private static AccessRequest request(Account account, String type, String database, @Nullable String collection, Instant now)
    {
        AccessRequest.Builder request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0])).resource(Planner.DATABASE, database).time(now);
        if (collection != null) {
            request.resource(Planner.COLLECTION, collection);
        }
        return request.build();
    }
}
