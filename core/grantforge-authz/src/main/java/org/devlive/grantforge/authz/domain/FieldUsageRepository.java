// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence of {@link FieldUsage}s. */
public interface FieldUsageRepository
        extends JpaRepository<FieldUsage, Long>
{
    /**
     * Returns where a field appears, by path and method.
     *
     * @param resourceId the field's resource
     * @return the usages
     */
    List<FieldUsage> findByResourceIdOrderByPathPatternAscHttpMethodAscDirectionAsc(long resourceId);
}
