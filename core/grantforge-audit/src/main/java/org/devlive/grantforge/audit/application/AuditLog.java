// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import jakarta.annotation.PreDestroy;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/** Records security events and reads them back. Events are only ever added; the retention job alone removes old ones. */
// One daemon thread writes refused calls after the fact, so recording them never holds up or fails the request.
@SuppressWarnings("PMD.DoNotUseThreads")
@Service
public final class AuditLog
{
    /** Most events waiting to be written by {@link #recordLater(AuditRecord)}. */
    static final int QUEUE = 10_000;

    private static final Logger LOG = LoggerFactory.getLogger(AuditLog.class);

    private final AuditEventRepository events;
    private final TransactionTemplate own;
    private final TransactionTemplate joined;
    private final TransactionTemplate reads;
    private final Clock clock;
    private final ExecutorService later = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE),
            runnable -> {
                Thread thread = new Thread(runnable, "grantforge-audit");
                thread.setDaemon(true);
                return thread;
            }, (runnable, executor) -> LOG.warn("Audit queue full: an event was dropped"));

    /**
     * Creates the log.
     *
     * @param events the event store
     * @param transactionManager opens transactions
     * @param clock source of event times
     */
    public AuditLog(AuditEventRepository events, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.events = requireNonNull(events, "events");
        requireNonNull(transactionManager, "transactionManager");
        // An event is kept even when the work it describes rolls back, such as a refused sign-in.
        this.own = new TransactionTemplate(transactionManager);
        own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // A change of permissions and its event commit or roll back together.
        this.joined = new TransactionTemplate(transactionManager);
        joined.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        this.reads = new TransactionTemplate(transactionManager);
        reads.setReadOnly(true);
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Records an event with the current time and the origin of the current request ({@link AuditContext}),
     * in a transaction of its own.
     *
     * @param event the event
     */
    public void record(AuditRecord event)
    {
        requireNonNull(event, "event");
        own.executeWithoutResult(status -> events.save(eventOf(event)));
    }

    /**
     * Records an event of a change in the transaction that makes the change, as changes of permissions are: if the change
     * rolls back, so does its event, and a change whose event cannot be written does not happen.
     *
     * @param event the event
     * @throws org.springframework.transaction.IllegalTransactionStateException if no transaction is active
     */
    public void recordWithChange(AuditRecord event)
    {
        requireNonNull(event, "event");
        joined.executeWithoutResult(status -> events.save(eventOf(event)));
    }

    /**
     * Records an event without waiting for it to be written, for events that must not slow down or fail the request they
     * describe, such as refused calls. Events beyond what the queue holds are dropped with a warning.
     *
     * @param event the event
     */
    public void recordLater(AuditRecord event)
    {
        requireNonNull(event, "event");
        AuditEvent built = eventOf(event);
        later.execute(() -> {
            try {
                own.executeWithoutResult(status -> events.save(built));
            }
            catch (RuntimeException failed) {
                LOG.warn("Could not record audit event {}", event.action(), failed);
            }
        });
    }

    /** Writes the events still waiting, for a while, when the application stops. */
    @PreDestroy
    void close()
    {
        later.shutdown();
        try {
            if (!later.awaitTermination(5, TimeUnit.SECONDS)) {
                later.shutdownNow();
            }
        }
        catch (InterruptedException interrupted) {
            later.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private AuditEvent eventOf(AuditRecord event)
    {
        RequestOrigin origin = AuditContext.current();
        return AuditEvent.of(clock.instant(), event.action(), event.outcome(), event.tenantId(), event.actorId(), event.actorName(),
                event.targetId(), event.reason(), origin.clientIp(), origin.userAgent(), origin.requestId());
    }

    /**
     * Returns an account's own events of some kinds, newest first.
     *
     * @param actorId the account
     * @param actions the kinds of event
     * @param page the page
     * @return the entries
     */
    public PageResult<AuditEntry> history(long actorId, Set<AuditAction> actions, PageQuery page)
    {
        return requireNonNull(reads.execute(status -> {
            Page<AuditEvent> found = events.findByActorIdAndActionInOrderByOccurredAtDescIdDesc(actorId, actions,
                    PageRequest.of(page.page() - 1, page.size()));
            return new PageResult<>(found.getContent().stream().map(AuditEntry::from).toList(), page.page(), page.size(),
                    found.getTotalElements());
        }));
    }
}
