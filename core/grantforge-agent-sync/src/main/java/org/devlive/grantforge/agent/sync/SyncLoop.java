// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * One sync of a system with the policies, as every agent beside a system runs it. Without a snapshot nothing changes.
 * With one, the agent reads what the system holds and applies the snapshot at the present moment, so that policies with
 * validity periods start and end on time; a snapshot of a service that is not in use, or of another service type,
 * grants nothing, and the agent takes back what it gave. Each turn says what changed when something did or the policy
 * version moved.
 *
 * <p>The agent reads and applies in its system's own terms. Syncs never overlap: one from a timer and one asked for
 * now run one after the other. The log is the subclass's own logger.
 *
 * @param <S> what the agent reads from the system
 * @param <O> what one sync changed
 */
public abstract class SyncLoop<S, O>
{
    /**
     * How the log names the protected system.
     *
     * @param name the system's name, such as {@code ClickHouse}
     * @param serviceType the service type whose snapshots the agent takes, such as {@code clickhouse}
     * @param ownControl what decides access when GrantForge does not, such as {@code ClickHouse's own grants}
     */
    public record Target(String name, String serviceType, String ownControl)
    {
    }

    private final Target system;
    private final ReentrantLock running = new ReentrantLock();
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Clock clock;
    private final Logger log;
    private boolean waiting;
    private String reportedState = "";
    private long version = Long.MIN_VALUE;

    /**
     * Prepares the sync.
     *
     * @param system how the log names the system
     * @param snapshots the current snapshot, {@code null} before the first
     * @param clock the clock policies are evaluated at
     */
    protected SyncLoop(Target system, Supplier<@Nullable Snapshot> snapshots, Clock clock)
    {
        this.system = system;
        this.snapshots = snapshots;
        this.clock = clock;
        this.log = Logger.getLogger(getClass().getName());
    }

    /**
     * Brings the system in step with the policies once.
     *
     * @return what changed, or {@code null} without a snapshot
     */
    public final @Nullable O run()
    {
        running.lock();
        try {
            return runOnce();
        }
        finally {
            running.unlock();
        }
    }

    private @Nullable O runOnce()
    {
        Snapshot snapshot = snapshots.get();
        if (snapshot == null) {
            if (!waiting) {
                log.info("The GrantForge " + system.name() + " agent waits for its first policy snapshot; " + system.name() + " is left as it is");
                waiting = true;
            }
            return null;
        }
        S state = read(snapshot);
        Instant now = clock.instant();
        Snapshot granting = null;
        if (!system.serviceType().equals(snapshot.serviceType())) {
            report("The agent's token is bound to a " + snapshot.serviceType() + " service, not " + article(system.name()) + " " + system.name()
                    + " one; GrantForge grants nothing");
        }
        else if (!snapshot.serviceEnabled()) {
            report("The " + system.name() + " service " + snapshot.service() + " is not in use; GrantForge grants nothing and " + system.ownControl()
                    + " apply");
        }
        else {
            reportedState = "";
            granting = snapshot;
        }
        O outcome = apply(state, granting, now);
        if (changed(outcome) || version != snapshot.policyVersion()) {
            log.info("GrantForge policy version " + snapshot.policyVersion() + " applied to " + system.name() + ": " + summary(outcome));
            version = snapshot.policyVersion();
        }
        return outcome;
    }

    /**
     * Reads what the system holds now: its users, the agent's roles and what the policies can name.
     *
     * @param snapshot the snapshot about to be applied, which names the users of the policies
     * @return what the system holds
     */
    protected abstract S read(Snapshot snapshot);

    /**
     * Brings the system in step: gives each user what the snapshot gives at this moment, or takes back everything the
     * agent gave when the snapshot grants nothing.
     *
     * @param state what the system holds
     * @param snapshot the snapshot to grant from, or {@code null} to grant nothing
     * @param now the moment policies are evaluated at
     * @return what changed
     */
    protected abstract O apply(S state, @Nullable Snapshot snapshot, Instant now);

    /**
     * Tells whether a sync changed anything in the system.
     *
     * @param outcome what the sync did
     * @return whether it changed something
     */
    protected abstract boolean changed(O outcome);

    /**
     * Says what a sync did, for the log line after {@code applied to <system>:}.
     *
     * @param outcome what the sync did
     * @return the counts, such as {@code 3 users, 1 role created}
     */
    protected abstract String summary(O outcome);

    private void report(String state)
    {
        if (!state.equals(reportedState)) {
            log.severe(state);
            reportedState = state;
        }
    }

    /** "an OpenSearch one", "a ClickHouse one". */
    static String article(String name)
    {
        return !name.isEmpty() && "AEIOUaeiou".indexOf(name.charAt(0)) >= 0 ? "an" : "a";
    }
}
