// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Persistence of {@link AuditEvent}s; events are only ever inserted. */
public interface AuditEventRepository
        extends JpaRepository<AuditEvent, Long>
{
    /**
     * Returns an actor's events of some kinds, newest first.
     *
     * @param actorId the account that acted
     * @param actions the kinds of event
     * @param page the page
     * @return the events
     */
    Page<AuditEvent> findByActorIdAndActionInOrderByOccurredAtDescIdDesc(long actorId, Collection<AuditAction> actions,
            Pageable page);

    /**
     * Returns the oldest events before a moment, oldest first.
     *
     * @param before the moment
     * @param page how many to return
     * @return the events
     */
    @Query("select e from AuditEvent e where e.occurredAt < :before order by e.occurredAt, e.id")
    List<AuditEvent> findOlderThan(@Param("before") Instant before, Pageable page);
}
