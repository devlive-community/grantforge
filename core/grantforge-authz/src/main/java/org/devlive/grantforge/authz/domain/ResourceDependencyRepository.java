// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence of {@link ResourceDependency} rows. */
public interface ResourceDependencyRepository
        extends JpaRepository<ResourceDependency, Long>
{
    /**
     * Returns every dependency of an application.
     *
     * @param applicationId the application
     * @return the dependencies
     */
    List<ResourceDependency> findByApplicationId(long applicationId);

    /**
     * Returns what a resource needs.
     *
     * @param resourceId the resource
     * @return its dependencies
     */
    List<ResourceDependency> findByResourceId(long resourceId);

    /**
     * Returns what needs a resource.
     *
     * @param dependsOnId the resource
     * @return the dependencies on it
     */
    List<ResourceDependency> findByDependsOnId(long dependsOnId);

    /**
     * Finds the dependency between two resources.
     *
     * @param resourceId the resource that needs the other
     * @param dependsOnId the resource it needs
     * @return the dependency, if any
     */
    Optional<ResourceDependency> findByResourceIdAndDependsOnId(long resourceId, long dependsOnId);

    /**
     * Tells whether anything needs a resource.
     *
     * @param dependsOnId the resource
     * @return {@code true} if some resource depends on it
     */
    boolean existsByDependsOnId(long dependsOnId);
}
