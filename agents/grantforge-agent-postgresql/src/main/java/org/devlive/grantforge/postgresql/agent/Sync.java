// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.SyncLoop;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One sync of PostgreSQL with the policies: reads what the server holds, plans each user's privileges and row filters
 * from the snapshot at the present moment, so that policies with validity periods start and end on time, applies the
 * plans and warns of privileges outside GrantForge that give what the policies deny. Without a snapshot nothing changes;
 * a snapshot of a service that is not in use, or of another service type, grants nothing.
 */
final class Sync
        extends SyncLoop<ServerState, Reconciler.Outcome>
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "postgresql";

    private static final Logger LOG = Logger.getLogger(Sync.class.getName());

    private final PostgreSql postgres;
    private final AgentConfig config;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts = new NativeConflicts();
    private final Notices notices = new Notices(LOG);
    private final AtomicReference<Map<String, Set<String>>> groups = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, Inventory>> inventories = new AtomicReference<>(Map.of());

    Sync(PostgreSql postgres, AgentConfig config, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        super(new Target("PostgreSQL", SERVICE_TYPE, "PostgreSQL's own privileges"), snapshots, clock);
        this.postgres = postgres;
        this.config = config;
        this.reconciler = new Reconciler(postgres, config.connection().database(), config.rolePrefix());
    }

    /**
     * Reads what the server holds and remembers its users' groups and what policies can name.
     *
     * @throws PostgreSqlFailure when the server fails a query or cannot be reached
     */
    @Override
    protected ServerState read(Snapshot snapshot)
    {
        ServerState state = ServerState.read(postgres, config.connection().database(), config.rolePrefix(), config.unmanaged(), config.databases());
        Map<String, Set<String>> held = new HashMap<>();
        state.accounts().forEach(account -> held.put(account.name(), account.groups()));
        groups.set(held);
        Map<String, Inventory> read = new HashMap<>();
        state.databases().forEach((name, database) -> read.put(name, database.inventory()));
        inventories.set(read);
        for (String user : state.superusers()) {
            notices.once(Level.INFO, "PostgreSQL user " + user + " is a superuser, whom privileges and row security do not bind; GrantForge keeps no"
                    + " role for it");
        }
        state.unreadable().forEach((database, why) -> notices.once(Level.INFO, "The GrantForge agent cannot read database " + database
                + ", which it leaves as it is: " + why));
        return state;
    }

    /**
     * Plans each user's access from the snapshot, applies the plans and warns of access outside GrantForge that gives what
     * the policies deny.
     *
     * @throws PostgreSqlFailure when the server fails a query or cannot be reached
     */
    @Override
    protected Reconciler.Outcome apply(ServerState state, @Nullable Snapshot snapshot, Instant now)
    {
        Function<Account, Plan> plans = null;
        if (snapshot != null) {
            Planner planner = new Planner(snapshot, state.databases(), now);
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
        return outcome.users() + " users, " + outcome.created() + " roles created, " + outcome.dropped() + " dropped, " + outcome.statements()
                + " grants and revokes, " + outcome.rowPolicies() + " row security policies changed"
                + (outcome.failed() > 0 ? ", " + outcome.failed() + " failed" : "");
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
