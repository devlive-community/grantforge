// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.SyncLoop;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One sync of Redis with the policies: reads every node's users, plans each user's selectors from the snapshot at the
 * present moment, so that policies with validity periods start and end on time, applies the plans on every node and
 * warns of what the agent cannot take away. Without a snapshot nothing changes; a snapshot of a service that is not in
 * use, or of another service type, grants nothing.
 */
final class Sync
        extends SyncLoop<ServerState, Reconciler.Outcome>
{
    /** The service type whose snapshots the agent takes. */
    static final String SERVICE_TYPE = "redis";

    private static final Logger LOG = Logger.getLogger(Sync.class.getName());

    private final Set<String> unmanaged;
    private final Redis redis;
    private final Reconciler reconciler;
    private final NativeConflicts conflicts;
    private final Notices notices = new Notices(LOG);

    /**
     * Syncs a deployment.
     *
     * @param redis the deployment
     * @param config the settings
     * @param self the agent's own user, which is left alone
     * @param snapshots the policies
     * @param clock the present
     */
    Sync(Redis redis, AgentConfig config, String self, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        super(new Target("Redis", SERVICE_TYPE, "Redis's own ACL"), snapshots, clock);
        this.redis = redis;
        Set<String> left = new TreeSet<>(config.unmanaged());
        left.add(self);
        this.unmanaged = Set.copyOf(left);
        this.reconciler = new Reconciler(redis);
        this.conflicts = new NativeConflicts(redis);
    }

    @Override
    protected ServerState read(Snapshot snapshot)
    {
        return ServerState.read(redis);
    }

    @Override
    protected Reconciler.Outcome apply(ServerState state, @Nullable Snapshot snapshot, Instant now)
    {
        Set<String> users = new TreeSet<>();
        for (String user : state.names()) {
            if (!unmanaged.contains(user) && !state.superuser(user)) {
                users.add(user);
            }
        }
        Function<String, Plan> plans = user -> Plan.NOTHING;
        if (snapshot != null) {
            Planner planner = new Planner(snapshot, state.topology().addresses(), now, reason -> notices.once(Level.INFO, "GrantForge withholds " + reason));
            plans = planner::plan;
        }
        Reconciler.Outcome outcome = reconciler.reconcile(state, users, plans);
        if (snapshot != null) {
            conflicts.check(state, users, snapshot, now);
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
        return outcome.users() + " users, " + outcome.replaced() + " users' selectors replaced, " + outcome.saved() + " ACL files saved"
                + (outcome.failed() > 0 ? ", " + outcome.failed() + " failed" : "");
    }
}
