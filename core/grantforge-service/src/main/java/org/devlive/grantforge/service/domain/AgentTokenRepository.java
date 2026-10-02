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

/** The agent tokens of the bound tenant's services. */
public interface AgentTokenRepository
        extends JpaRepository<AgentToken, Long>
{
    /**
     * Finds a token by its hash; called across tenants to sign agents in.
     *
     * @param tokenHash the SHA-256 hash of the token
     * @return the token, if there is one
     */
    Optional<AgentToken> findByTokenHash(String tokenHash);

    /**
     * Returns the tokens of a service, newest first.
     *
     * @param serviceId the service
     * @return the tokens
     */
    List<AgentToken> findByServiceIdOrderByCreatedAtDescIdDesc(long serviceId);

    /**
     * Removes every token of a service.
     *
     * @param serviceId the service
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AgentToken t where t.serviceId = :serviceId")
    int removeService(@Param("serviceId") long serviceId);
}
