// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

/**
 * Tenant-filtered DirectoryDepartment persistence.
 */
public interface DirectoryDepartmentRepository extends JpaRepository<DirectoryDepartment, Long>
{
    /**
     * Lists records of a data source.
     *
     * @param sourceId the data source
     * @return its records
     */
    List<DirectoryDepartment> findBySourceId(long sourceId);

    /**
     * Finds mappings for the specified visible departments.
     *
     * @param orgUnitIds department IDs
     * @return their directory mappings
     */
    List<DirectoryDepartment> findByOrgUnitIdIn(Collection<Long> orgUnitIds);
}
