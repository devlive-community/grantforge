// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence of {@link OAuthClient}s. */
public interface OAuthClientRepository
        extends JpaRepository<OAuthClient, Long>
{
    /**
     * Returns an application's clients, oldest first.
     *
     * @param applicationId the application
     * @return the clients
     */
    List<OAuthClient> findByApplicationIdOrderByCreatedAtAscIdAsc(long applicationId);

    /**
     * Finds a client by its public identifier.
     *
     * @param clientId the client ID
     * @return the client, if any
     */
    Optional<OAuthClient> findByClientId(String clientId);

    /**
     * Tells whether an application has clients.
     *
     * @param applicationId the application
     * @return {@code true} if it has any
     */
    boolean existsByApplicationId(long applicationId);
}
