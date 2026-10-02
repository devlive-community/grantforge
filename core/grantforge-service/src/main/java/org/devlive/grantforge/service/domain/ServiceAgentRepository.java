// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** The agents of the bound tenant's services. */
public interface ServiceAgentRepository
        extends JpaRepository<ServiceAgent, Long>
{
    /**
     * Finds an agent of a service by the name it gives itself.
     *
     * @param serviceId the service
     * @param instance the name
     * @return the agent, if it reported before
     */
    Optional<ServiceAgent> findByServiceIdAndInstance(long serviceId, String instance);

    /**
     * Returns the agents of a service by name.
     *
     * @param serviceId the service
     * @return the agents
     */
    List<ServiceAgent> findByServiceIdOrderByInstanceAsc(long serviceId);

    /**
     * Removes every agent of a service.
     *
     * @param serviceId the service
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ServiceAgent a where a.serviceId = :serviceId")
    int removeService(@Param("serviceId") long serviceId);
}
