// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** The services of the bound tenant. */
public interface ManagedServiceRepository
        extends JpaRepository<ManagedService, Long>
{
    /**
     * Returns the services by name.
     *
     * @return the services
     */
    List<ManagedService> findAllByOrderByNameAsc();

    /**
     * Finds a service by name.
     *
     * @param name the name
     * @return the service, if there is one
     */
    Optional<ManagedService> findByName(String name);
}
