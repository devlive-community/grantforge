// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.ConditionEvaluator;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An agent's link to GrantForge: keeps the policy snapshot of its service current and decides access with it, and
 * ships the access events the system records. Thread-safe; {@link #decide} and {@link #record} never wait for the
 * server.
 *
 * <p>On {@link #start} the agent takes the snapshot it stored last, if its signature still holds, then sends
 * heartbeats as often as the server asks. When the server's policy version differs from the applied one, it
 * downloads the snapshot, checks its signature with the server's key (pinned in the settings, or fetched once and
 * kept), builds an engine from it and stores it; a snapshot that fails any step is not applied and the previous one
 * stays. Until a snapshot is applied, every decision is {@link AgentDecision.Outcome#NOT_DETERMINED}: the agent of a
 * system decides whether that falls back to the system's own checks or denies.
 */
// An agent keeps its policies current and ships events in the background, beside the system it lives in; the snapshot
// it applies is published to the system's threads through volatile fields.
@SuppressWarnings({"PMD.DoNotUseThreads", "PMD.AvoidUsingVolatile"})
public final class GrantForgeAgent
        implements AutoCloseable
{
    private static final Logger LOG = Logger.getLogger(GrantForgeAgent.class.getName());

    private final AgentSettings settings;
    private final Map<String, ConditionEvaluator> evaluators;
    private final ServerClient client;
    private final SnapshotStore store;
    private final AuditShipper shipper;
    private final ScheduledExecutorService scheduler;
    private volatile @Nullable Applied applied;
    private volatile @Nullable SigningKey key;
    private volatile boolean reachable = true;

    // The pool starts no thread until a task is scheduled, which only start() does.
    GrantForgeAgent(AgentSettings settings, Map<String, ConditionEvaluator> evaluators)
    {
        this.settings = settings;
        this.evaluators = Collections.unmodifiableMap(new HashMap<>(evaluators));
        this.client = new ServerClient(settings);
        this.store = new SnapshotStore(settings.cacheDirectory());
        this.shipper = new AuditShipper(settings, client);
        this.key = settings.trustedKey();
        this.scheduler = Executors.newScheduledThreadPool(2, new AgentThreads(settings.instance()));
    }

    /**
     * Starts an agent: applies the stored snapshot, then keeps the snapshot current and ships events in the background.
     *
     * @param settings the settings
     * @param evaluators the evaluators of the conditions the service type defines, by evaluator name
     * @return the running agent; {@link #close} it when the system stops
     */
    public static GrantForgeAgent start(AgentSettings settings, Map<String, ConditionEvaluator> evaluators)
    {
        GrantForgeAgent agent = new GrantForgeAgent(settings, evaluators);
        agent.loadStored();
        agent.scheduler.execute(agent::refreshAndReschedule);
        long flush = settings.auditFlushInterval().toMillis();
        agent.scheduler.scheduleWithFixedDelay(agent::shipQuietly, flush, flush, TimeUnit.MILLISECONDS);
        return agent;
    }

    /**
     * Decides a request with the applied snapshot.
     *
     * @param request the request; the agent adds the roles and groups the snapshot gives its user
     * @return the decision; {@link AgentDecision.Outcome#NOT_DETERMINED} without a snapshot
     */
    public AgentDecision decide(AccessRequest request)
    {
        Applied current = applied;
        return current == null ? AgentDecision.withoutSnapshot() : current.snapshot.decide(request);
    }

    /**
     * Queues an access event for the server.
     *
     * @param event the event
     * @return {@code false} if too many events wait and this one was dropped
     */
    public boolean record(AccessEvent event)
    {
        return shipper.record(event);
    }

    /**
     * Returns the applied snapshot.
     *
     * @return the snapshot, or {@code null} before one is applied
     */
    public @Nullable Snapshot snapshot()
    {
        Applied current = applied;
        return current == null ? null : current.snapshot;
    }

    /**
     * Returns whether the last call to the server succeeded.
     *
     * @return {@code true} if it did
     */
    public boolean serverReachable()
    {
        return reachable;
    }

    /**
     * Returns how many access events were dropped, because too many waited or the spool was full.
     *
     * @return the count
     */
    public long droppedEvents()
    {
        return shipper.dropped();
    }

    /**
     * Returns how many access events wait in memory to be shipped.
     *
     * @return the count
     */
    public int queuedEvents()
    {
        return shipper.waiting();
    }

    /** Applies the stored snapshot, if there is one whose signature holds. */
    void loadStored()
    {
        try {
            SnapshotStore.Stored stored = store.load(settings.trustedKey());
            if (stored == null) {
                return;
            }
            apply(stored.snapshot(), stored.key(), false);
            LOG.info("Applied the stored policy snapshot of version " + policyVersion());
        }
        catch (IOException | IllegalArgumentException unusable) {
            LOG.log(Level.WARNING, "The stored policy snapshot cannot be used; waiting for the server", unusable);
        }
    }

    /**
     * Sends a heartbeat and applies a new snapshot if the policy version changed.
     *
     * @return the seconds until the next heartbeat the server asks for, or the configured interval if it cannot be
     *         reached
     */
    long refresh()
    {
        long fallback = settings.refreshInterval().getSeconds();
        try {
            Long version = policyVersion();
            ServerClient.Heartbeat heartbeat = client.heartbeat(version);
            if (version == null || heartbeat.policyVersion() != version) {
                Applied current = applied;
                ServerClient.Download download = client.policies(current == null ? null : current.etag);
                if (download.changed()) {
                    apply(download, keyFor(download.keyId()), true);
                    LOG.info("Applied policy snapshot version " + policyVersion());
                }
            }
            reachable = true;
            return heartbeat.refreshSeconds() > 0 ? heartbeat.refreshSeconds() : fallback;
        }
        catch (IOException unreachable) {
            if (reachable) {
                LOG.log(Level.WARNING, "GrantForge cannot be reached; keeping the applied policies", unreachable);
            }
            reachable = false;
            return fallback;
        }
        catch (IllegalArgumentException | SecurityException rejected) {
            reachable = true;
            LOG.log(Level.WARNING, "The policy snapshot was rejected; keeping the applied policies", rejected);
            return fallback;
        }
    }

    /** The key that signed a snapshot: the pinned one, the one fetched before, or the server's current one. */
    private SigningKey keyFor(String keyId) throws IOException
    {
        SigningKey known = key;
        if (known != null && known.keyId().equals(keyId)) {
            return known;
        }
        if (settings.trustedKey() != null) {
            throw new SecurityException("the snapshot is signed with key " + keyId + ", not with the pinned key");
        }
        SigningKey fetched = client.signingKey();
        if (!fetched.keyId().equals(keyId)) {
            throw new SecurityException("the snapshot is signed with key " + keyId + ", the server publishes " + fetched.keyId());
        }
        key = fetched;
        return fetched;
    }

    private void apply(ServerClient.Download download, SigningKey signer, boolean save) throws IOException
    {
        if (!signer.keyId().equals(download.keyId()) || !signer.verifies(download.body(), download.signature())) {
            throw new SecurityException("the policy snapshot's signature does not hold");
        }
        Snapshot snapshot = Snapshot.parse(download.body(), evaluators);
        applied = new Applied(snapshot, download.etag());
        key = signer;
        if (save) {
            try {
                store.save(download, signer);
            }
            catch (IOException unwritable) {
                LOG.log(Level.WARNING, "The policy snapshot cannot be stored in " + settings.cacheDirectory(), unwritable);
            }
        }
    }

    private @Nullable Long policyVersion()
    {
        Applied current = applied;
        return current == null ? null : current.snapshot.policyVersion();
    }

    /**
     * Sends the waiting events.
     */
    void ship()
    {
        shipper.ship(true);
    }

    // A failure here must not stop the scheduled task from running again.
    private void shipQuietly()
    {
        try {
            ship();
        }
        catch (RuntimeException unexpected) {
            LOG.log(Level.WARNING, "Shipping access events failed", unexpected);
        }
    }

    // A failure here must not end the heartbeats.
    private void refreshAndReschedule()
    {
        long delay = settings.refreshInterval().getSeconds();
        try {
            delay = refresh();
        }
        catch (RuntimeException unexpected) {
            LOG.log(Level.WARNING, "Refreshing the policies failed", unexpected);
        }
        if (!scheduler.isShutdown()) {
            scheduler.schedule(this::refreshAndReschedule, delay, TimeUnit.SECONDS);
        }
    }

    /** Stops the background work and sends the events that wait, or spools them. */
    @Override
    public void close()
    {
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(settings.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        ship();
    }

    /** The applied snapshot with the ETag it was downloaded with. */
    private static final class Applied
    {
        final Snapshot snapshot;
        final String etag;

        Applied(Snapshot snapshot, String etag)
        {
            this.snapshot = snapshot;
            this.etag = etag;
        }
    }

    /** Daemon threads named after the agent, so they never keep the system from stopping. */
    private static final class AgentThreads
            implements ThreadFactory
    {
        private final String instance;
        private final AtomicInteger count = new AtomicInteger();

        AgentThreads(String instance)
        {
            this.instance = instance;
        }

        @Override
        public Thread newThread(Runnable task)
        {
            Thread thread = new Thread(task, "grantforge-agent-" + instance + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
