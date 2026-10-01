// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditEventRepositoryTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Autowired
    private AuditEventRepository events;

    @AfterEach
    void deleteRows()
    {
        events.deleteAllInBatch();
    }

    private void save(long actor, AuditAction action, int minutes)
    {
        events.save(AuditEvent.of(NOW.plusSeconds(60L * minutes), action, AuditOutcome.SUCCESS, 1L, actor, null, null,
                null, null, null, null));
    }

    @Test
    void listsAnActorsEventsOfSomeKindsNewestFirst()
    {
        save(7, AuditAction.LOGIN_SUCCEEDED, 1);
        save(7, AuditAction.PASSWORD_CHANGED, 2);
        save(7, AuditAction.LOGOUT, 3);
        save(8, AuditAction.LOGIN_SUCCEEDED, 4);

        var page = events.findByActorIdAndActionInOrderByOccurredAtDescIdDesc(7,
                Set.of(AuditAction.LOGIN_SUCCEEDED, AuditAction.LOGOUT), PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(AuditEvent::getAction).containsExactly(AuditAction.LOGOUT);
    }
}
