// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * One sync of ClickHouse with the policies: reads what the server holds, plans each user's privileges and row filters
 * from the snapshot at the present moment, so that policies with validity periods start and end on time, applies the
 * plans and warns of grants outside GrantForge that give what the policies deny. Without a snapshot nothing changes; a
 * snapshot of a service that is not in use, or of another service type, grants nothing.
 */
final class Sync
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "clickhouse";

    private static final Logger LOG = Logger.getLogger(Sync.class.getName());

    private final ClickHouse clickhouse;
    private final AgentConfig config;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Clock clock;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts = new NativeConflicts();
    private final Set<String> reportedReadOnly = new HashSet<>();
    private final ReentrantLock running = new ReentrantLock();
    private final AtomicReference<Map<String, Set<String>>> groups = new AtomicReference<>(Map.of());
    private final AtomicReference<@Nullable Inventory> inventory = new AtomicReference<>();
    private boolean waiting;
    private String reportedState = "";
    private long version = Long.MIN_VALUE;

    Sync(ClickHouse clickhouse, AgentConfig config, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        this.clickhouse = clickhouse;
        this.config = config;
        this.snapshots = snapshots;
        this.clock = clock;
        this.reconciler = new Reconciler(clickhouse, config.rolePrefix());
    }

    /**
     * Brings ClickHouse in step with the policies once.
     *
     * @return what changed, or {@code null} without a snapshot
     * @throws ClickHouseFailure when the server fails a query or cannot be reached
     */
    Reconciler.@Nullable Outcome run()
    {
        running.lock();
        try {
            return runOnce();
        }
        finally {
            running.unlock();
        }
    }

    private Reconciler.@Nullable Outcome runOnce()
    {
        Snapshot snapshot = snapshots.get();
        if (snapshot == null) {
            if (!waiting) {
                LOG.info("The GrantForge ClickHouse agent waits for its first policy snapshot; ClickHouse is left as it is");
                waiting = true;
            }
            return null;
        }
        ServerState state = ServerState.read(clickhouse, config.rolePrefix(), config.unmanaged());
        Map<String, Set<String>> held = new HashMap<>();
        state.accounts().forEach(account -> held.put(account.name(), account.groups()));
        groups.set(held);
        inventory.set(state.inventory());
        for (String user : state.readOnly()) {
            if (reportedReadOnly.add(user)) {
                LOG.info("ClickHouse user " + user + " is stored where nothing can change it, such as users.xml; GrantForge keeps no role for it");
            }
        }
        Instant now = clock.instant();
        Function<Account, Plan> plans = null;
        if (!SERVICE_TYPE.equals(snapshot.serviceType())) {
            report("The agent's token is bound to a " + snapshot.serviceType() + " service, not a ClickHouse one; GrantForge grants nothing");
        }
        else if (!snapshot.serviceEnabled()) {
            report("The ClickHouse service " + snapshot.service() + " is not in use; GrantForge grants nothing and ClickHouse's own grants apply");
        }
        else {
            reportedState = "";
            Planner planner = new Planner(snapshot, state.inventory(), now);
            plans = planner::plan;
        }
        Reconciler.Outcome outcome = reconciler.reconcile(state, plans);
        if (plans != null) {
            conflicts.check(state, snapshot, now);
        }
        if (outcome.changed() || version != snapshot.policyVersion()) {
            LOG.info("GrantForge policy version " + snapshot.policyVersion() + " applied to ClickHouse: " + outcome.users() + " users, "
                    + outcome.roles() + " roles set, " + outcome.dropped() + " dropped, " + outcome.rowPolicies() + " row policies changed"
                    + (outcome.failed() > 0 ? ", " + outcome.failed() + " users failed" : ""));
            version = snapshot.policyVersion();
        }
        return outcome;
    }

    private void report(String state)
    {
        if (!state.equals(reportedState)) {
            LOG.severe(state);
            reportedState = state;
        }
    }

    /**
     * Returns the ClickHouse roles a user held at the last sync, which policies name as groups.
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
     * @return its columns, none before the first sync or for a table the server did not have
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
