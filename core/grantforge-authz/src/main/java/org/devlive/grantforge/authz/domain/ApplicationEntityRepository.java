// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Data entities of applications other than the console. */
public interface ApplicationEntityRepository
        extends JpaRepository<ApplicationEntity, Long>
{
    /**
     * Lists an application's entities by code.
     *
     * @param applicationId the application
     * @return the entities
     */
    List<ApplicationEntity> findByApplicationIdOrderByCode(long applicationId);

    /**
     * Lists every application's entities by code.
     *
     * @return the entities
     */
    List<ApplicationEntity> findAllByOrderByCode();

    /**
     * Finds an entity by its full code.
     *
     * @param code the code
     * @return the entity
     */
    Optional<ApplicationEntity> findByCode(String code);

    /**
     * Returns whether an application declared entities.
     *
     * @param applicationId the application
     * @return {@code true} if it has some
     */
    boolean existsByApplicationId(long applicationId);
}
