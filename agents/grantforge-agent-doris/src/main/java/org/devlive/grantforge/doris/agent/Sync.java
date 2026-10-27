// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.SyncLoop;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One sync of Doris with the policies: reads what the cluster holds, plans each user's privileges and row filters from
 * the snapshot at the present moment, so that policies with validity periods start and end on time, applies the plans
 * and warns of grants outside GrantForge that give what the policies deny. Without a snapshot nothing changes; a
 * snapshot of a service that is not in use, or of another service type, grants nothing.
 */
final class Sync
        extends SyncLoop<ServerState, Reconciler.Outcome>
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "doris";

    private static final Logger LOG = Logger.getLogger(Sync.class.getName());

    private final Doris doris;
    private final AgentConfig config;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts = new NativeConflicts();
    private final Notices notices = new Notices(LOG);
    private final AtomicReference<Map<String, Set<String>>> groups = new AtomicReference<>(Map.of());
    private final AtomicReference<@Nullable Inventory> inventory = new AtomicReference<>();

    Sync(Doris doris, AgentConfig config, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        super(new Target("Doris", SERVICE_TYPE, "Doris's own grants"), snapshots, clock);
        this.doris = doris;
        this.config = config;
        this.reconciler = new Reconciler(doris, config.rolePrefix());
    }

    /**
     * Reads what the cluster holds and remembers its users' groups and what policies can name.
     *
     * @throws DorisFailure when the cluster fails a statement or cannot be reached
     */
    @Override
    protected ServerState read(Snapshot snapshot)
    {
        ServerState state = ServerState.read(doris, config.rolePrefix(), config.unmanaged());
        Map<String, Set<String>> held = new HashMap<>();
        state.accounts().forEach(account -> held.put(account.name(), account.groups()));
        groups.set(held);
        inventory.set(state.inventory());
        return state;
    }

    /**
     * Plans each user's access from the snapshot, applies the plans and warns of access outside GrantForge that gives what
     * the policies deny.
     *
     * @throws DorisFailure when the cluster fails a statement or cannot be reached
     */
    @Override
    protected Reconciler.Outcome apply(ServerState state, @Nullable Snapshot snapshot, Instant now)
    {
        Function<Account, Plan> plans = null;
        if (snapshot != null) {
            Set<String> catalogs = config.catalogs();
            Planner planner = new Planner(snapshot, state.inventory(), catalog -> catalogs.isEmpty() || catalogs.contains(catalog), now,
                    reason -> notices.once(Level.INFO, "GrantForge withholds " + reason));
            plans = planner::plan;
        }
        Reconciler.Outcome outcome = reconciler.reconcile(state, plans);
        if (snapshot != null) {
            conflicts.check(state, snapshot, reconciler.plans(), reconciler::role, now);
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
        return outcome.users() + " users, " + outcome.roles() + " roles created, " + outcome.dropped() + " dropped, " + outcome.privileges()
                + " privileges changed, " + outcome.rowPolicies() + " row policies changed" + (outcome.failed() > 0 ? ", " + outcome.failed()
                + " users failed" : "");
    }

    /**
     * Returns the Doris roles a user held at the last sync, which policies name as groups.
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
     * Returns the columns a table had at the last sync.
     *
     * @param table the table
     * @return its columns, none before the first sync
     */
    List<String> columnsOf(TableRef table)
    {
        Inventory last = inventory.get();
        return last == null ? List.of() : last.columns(table);
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
