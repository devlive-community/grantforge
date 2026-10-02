// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** The access events of the bound tenant's services; called across tenants only to purge old events. */
public interface AccessEventRepository
        extends JpaRepository<AccessEvent, Long>, JpaSpecificationExecutor<AccessEvent>
{
    /**
     * Returns which of some event ids a service has already.
     *
     * @param serviceId the service
     * @param eventIds the agent's names for events, at most 1000
     * @return those stored already
     */
    @Query("select e.eventId from AccessEvent e where e.serviceId = :serviceId and e.eventId in :eventIds")
    List<String> findKnownEventIds(@Param("serviceId") long serviceId, @Param("eventIds") Collection<String> eventIds);

    /**
     * Returns the ids of events older than a moment, oldest first.
     *
     * @param before the moment
     * @param page how many
     * @return the ids
     */
    @Query("select e.id from AccessEvent e where e.occurredAt < :before order by e.occurredAt, e.id")
    List<Long> findIdsBefore(@Param("before") Instant before, Pageable page);

    /**
     * Removes events.
     *
     * @param ids the events, at most 1000
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccessEvent e where e.id in :ids")
    int removeAll(@Param("ids") Collection<Long> ids);

    /**
     * Removes every event of a service.
     *
     * @param serviceId the service
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AccessEvent e where e.serviceId = :serviceId")
    int removeService(@Param("serviceId") long serviceId);
}
