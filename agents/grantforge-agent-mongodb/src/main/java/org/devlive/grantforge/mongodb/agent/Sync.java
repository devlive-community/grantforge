// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.SyncLoop;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One sync of MongoDB with the policies: reads what the deployment holds, plans each user's role from the snapshot at the
 * present moment, so that policies with validity periods start and end on time, applies the plans and warns of what
 * the agent cannot take away. Without a snapshot nothing changes; a snapshot of a service that is not in use, or of
 * another service type, grants nothing.
 */
final class Sync
        extends SyncLoop<ServerState, Reconciler.Outcome>
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "mongodb";

    private static final Logger LOG = Logger.getLogger(Sync.class.getName());

    private final MongoDb mongo;
    private final AgentConfig config;
    private final Set<String> unmanaged;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts = new NativeConflicts();
    private final Notices notices = new Notices(LOG);
    private final AtomicReference<Map<String, Set<String>>> groups = new AtomicReference<>(Map.of());
    private final AtomicReference<@Nullable Inventory> inventory = new AtomicReference<>();

    /**
     * Syncs a deployment.
     *
     * @param mongo the deployment
     * @param config the settings
     * @param self the agent's own user, which is left alone
     * @param snapshots the policies
     * @param clock the present
     */
    Sync(MongoDb mongo, AgentConfig config, String self, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        super(new Target("MongoDB", SERVICE_TYPE, "MongoDB's own roles"), snapshots, clock);
        this.mongo = mongo;
        this.config = config;
        Set<String> left = new HashSet<>(config.unmanaged());
        left.add(self);
        this.unmanaged = Set.copyOf(left);
        this.reconciler = new Reconciler(mongo, config.rolePrefix());
    }

    /**
     * Reads what the deployment holds and remembers its users' groups and what policies can name.
     *
     * @throws MongoFailure when the deployment fails a command or cannot be reached
     */
    @Override
    protected ServerState read(Snapshot snapshot)
    {
        ServerState state = ServerState.read(mongo, config.rolePrefix(), unmanaged);
        Map<String, Set<String>> held = new HashMap<>();
        state.accounts().forEach(account -> held.put(account.name(), account.groups()));
        groups.set(held);
        inventory.set(state.inventory());
        return state;
    }

    /**
     * Plans each user's role from the snapshot, applies the plans and warns of what the agent cannot take away.
     *
     * @throws MongoFailure when the deployment fails a command or cannot be reached
     */
    @Override
    protected Reconciler.Outcome apply(ServerState state, @Nullable Snapshot snapshot, Instant now)
    {
        Function<Account, Plan> plans = null;
        if (snapshot != null) {
            Set<String> databases = config.databases();
            Planner planner = new Planner(snapshot, state.inventory(), database -> databases.isEmpty() || databases.contains(database), now,
                    reason -> notices.once(Level.INFO, "GrantForge withholds " + reason));
            plans = planner::plan;
        }
        Reconciler.Outcome outcome = reconciler.reconcile(state, plans);
        if (snapshot != null) {
            conflicts.check(state, snapshot, now);
        }
        return outcome;
    }

    @Override
    protected boolean changed(Reconciler.Outcome outcome)
    {
        return outcome.changed();
    }

    @Override
    protected String summary(Reconciler.Outcome outcome)
    {
        return outcome.users() + " users, " + outcome.created() + " roles created, " + outcome.updated() + " replaced, " + outcome.dropped() + " dropped, "
                + outcome.grants() + " grants changed" + (outcome.failed() > 0 ? ", " + outcome.failed() + " failed" : "");
    }

    /**
     * Returns the MongoDB roles a user held at the last sync, which policies name as groups.
     *
     * @param user the user
     * @return the roles, none for a user the agent does not keep a role for
     */
    Set<String> groupsOf(String user)
    {
        Map<String, Set<String>> held = groups.get();
        return held == null ? Set.of() : held.getOrDefault(user, Set.of());
    }

    /**
     * Returns what the deployment had at the last sync.
     *
     * @return the inventory, or {@code null} before the first sync
     */
    @Nullable Inventory inventory()
    {
        return inventory.get();
    }

    /**
     * Returns the role the agent keeps for a user.
     *
     * @param user the user
     * @return the role's name
     */
    String role(String user)
    {
        return reconciler.role(user);
    }
}
