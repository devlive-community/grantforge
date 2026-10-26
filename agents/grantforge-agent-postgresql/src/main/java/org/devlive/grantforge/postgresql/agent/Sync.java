// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * One sync of PostgreSQL with the policies: reads what the server holds, plans each user's privileges and row filters
 * from the snapshot at the present moment, so that policies with validity periods start and end on time, applies the
 * plans and warns of privileges outside GrantForge that give what the policies deny. Without a snapshot nothing changes;
 * a snapshot of a service that is not in use, or of another service type, grants nothing.
 */
final class Sync
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "postgresql";

    private static final Logger LOG = Logger.getLogger(Sync.class.getName());

    private final PostgreSql postgres;
    private final AgentConfig config;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Clock clock;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts = new NativeConflicts();
    private final Set<String> reported = new HashSet<>();
    private final ReentrantLock running = new ReentrantLock();
    private final AtomicReference<Map<String, Set<String>>> groups = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, Inventory>> inventories = new AtomicReference<>(Map.of());
    private boolean waiting;
    private String reportedState = "";
    private long version = Long.MIN_VALUE;

    Sync(PostgreSql postgres, AgentConfig config, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        this.postgres = postgres;
        this.config = config;
        this.snapshots = snapshots;
        this.clock = clock;
        this.reconciler = new Reconciler(postgres, config.connection().database(), config.rolePrefix());
    }

    /**
     * Brings PostgreSQL in step with the policies once.
     *
     * @return what changed, or {@code null} without a snapshot
     * @throws PostgreSqlFailure when the server fails a query or cannot be reached
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
                LOG.info("The GrantForge PostgreSQL agent waits for its first policy snapshot; PostgreSQL is left as it is");
                waiting = true;
            }
            return null;
        }
        ServerState state = ServerState.read(postgres, config.connection().database(), config.rolePrefix(), config.unmanaged(), config.databases());
        Map<String, Set<String>> held = new HashMap<>();
        state.accounts().forEach(account -> held.put(account.name(), account.groups()));
        groups.set(held);
        Map<String, Inventory> read = new HashMap<>();
        state.databases().forEach((name, database) -> read.put(name, database.inventory()));
        inventories.set(read);
        for (String user : state.superusers()) {
            once("PostgreSQL user " + user + " is a superuser, whom privileges and row security do not bind; GrantForge keeps no role for it");
        }
        state.unreadable().forEach((database, why) -> once("The GrantForge agent cannot read database " + database + ", which it leaves as it is: "
                + why));
        Instant now = clock.instant();
        Function<Account, Plan> plans = null;
        if (!SERVICE_TYPE.equals(snapshot.serviceType())) {
            report("The agent's token is bound to a " + snapshot.serviceType() + " service, not a PostgreSQL one; GrantForge grants nothing");
        }
        else if (!snapshot.serviceEnabled()) {
            report("The PostgreSQL service " + snapshot.service() + " is not in use; GrantForge grants nothing and PostgreSQL's own privileges apply");
        }
        else {
            reportedState = "";
            Planner planner = new Planner(snapshot, state.databases(), now);
            plans = planner::plan;
        }
        Reconciler.Outcome outcome = reconciler.reconcile(state, plans);
        if (plans != null) {
            conflicts.check(state, snapshot, now);
        }
        if (outcome.changed() || version != snapshot.policyVersion()) {
            LOG.info("GrantForge policy version " + snapshot.policyVersion() + " applied to PostgreSQL: " + outcome.users() + " users, "
                    + outcome.created() + " roles created, " + outcome.dropped() + " dropped, " + outcome.statements() + " grants and revokes, "
                    + outcome.rowPolicies() + " row security policies changed" + (outcome.failed() > 0 ? ", " + outcome.failed() + " failed" : ""));
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

    private void once(String message)
    {
        if (reported.add(message)) {
            LOG.info(message);
        }
    }

    /**
     * Returns the roles a user was a member of at the last sync, which policies name as groups.
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
     * Returns the schemas, tables and columns a database had at the last sync.
     *
     * @param database the database
     * @return its inventory, empty before the first sync or for a database the agent does not keep
     */
    Inventory inventoryOf(String database)
    {
        Map<String, Inventory> last = inventories.get();
        return last == null ? Inventory.EMPTY : last.getOrDefault(database, Inventory.EMPTY);
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
