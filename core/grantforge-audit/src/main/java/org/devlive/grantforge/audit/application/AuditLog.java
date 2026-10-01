// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/** Records security events and reads them back. */
@Service
public final class AuditLog
{
    private final AuditEventRepository events;
    private final TransactionTemplate own;
    private final TransactionTemplate reads;
    private final Clock clock;

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
        RequestOrigin origin = AuditContext.current();
        own.executeWithoutResult(status -> events.save(AuditEvent.of(clock.instant(), event.action(), event.outcome(),
                event.tenantId(), event.actorId(), event.actorName(), event.targetId(), event.reason(),
                origin.clientIp(), origin.userAgent(), origin.requestId())));
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
