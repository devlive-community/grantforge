// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** The policies of the services of the bound tenant. */
public interface ServicePolicyRepository
        extends JpaRepository<ServicePolicy, Long>
{
    /**
     * Returns the policies of a kind of a service, by name.
     *
     * @param serviceId the service
     * @param policyType the kind
     * @return the policies
     */
    List<ServicePolicy> findByServiceIdAndPolicyTypeOrderByNameAsc(long serviceId, PolicyType policyType);

    /**
     * Finds a policy of a service by name.
     *
     * @param serviceId the service
     * @param name the name
     * @return the policy, if there is one
     */
    Optional<ServicePolicy> findByServiceIdAndName(long serviceId, String name);

    /**
     * Counts the policies of a service.
     *
     * @param serviceId the service
     * @return how many it has
     */
    long countByServiceId(long serviceId);

    /**
     * Removes every policy of a service.
     *
     * @param serviceId the service
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ServicePolicy p where p.serviceId = :serviceId")
    int removeService(@Param("serviceId") long serviceId);
}
