// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(AuditLog.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditLogTest
{
    @Autowired
    private AuditLog log;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteRows()
    {
        events.deleteAllInBatch();
    }

    private static AuditRecord login(long actor, AuditOutcome outcome)
    {
        return new AuditRecord(outcome == AuditOutcome.SUCCESS ? AuditAction.LOGIN_SUCCEEDED : AuditAction.LOGIN_FAILED,
                outcome, 1L, actor, "alice", null, outcome == AuditOutcome.SUCCESS ? null : "GF-IDENTITY-020");
    }

    @Test
    void recordsTheEventWithTheRequestOrigin()
    {
        try (AuditContext.Scope ignored = AuditContext.bind(new RequestOrigin("req-1", "10.0.0.1", "Firefox"))) {
            log.record(login(7, AuditOutcome.FAILURE));
        }

        AuditEvent saved = events.findAll().get(0);
        assertThat(saved).extracting(AuditEvent::getAction, AuditEvent::getActorId, AuditEvent::getReason,
                AuditEvent::getClientIp, AuditEvent::getUserAgent, AuditEvent::getRequestId)
                .containsExactly(AuditAction.LOGIN_FAILED, 7L, "GF-IDENTITY-020", "10.0.0.1", "Firefox", "req-1");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void keepsEventsWhenTheSurroundingWorkRollsBack()
    {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            log.record(login(7, AuditOutcome.FAILURE));
            status.setRollbackOnly();
        });

        assertThat(events.count()).isOne();
    }

    @Test
    void readsAnAccountsHistoryNewestFirst()
    {
        log.record(login(7, AuditOutcome.FAILURE));
        log.record(login(7, AuditOutcome.SUCCESS));
        log.record(login(8, AuditOutcome.SUCCESS));
        log.record(new AuditRecord(AuditAction.PASSWORD_CHANGED, AuditOutcome.SUCCESS, 1L, 7L, null, null, null));

        PageResult<AuditEntry> history = log.history(7, Set.of(AuditAction.LOGIN_SUCCEEDED, AuditAction.LOGIN_FAILED),
                new PageQuery(1, 10));

        assertThat(history.total()).isEqualTo(2);
        assertThat(history.items()).extracting(AuditEntry::outcome)
                .containsExactlyInAnyOrder(AuditOutcome.SUCCESS, AuditOutcome.FAILURE);
        assertThat(history.items().get(0).occurredAt()).isAfterOrEqualTo(history.items().get(1).occurredAt());
    }
}
