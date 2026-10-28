// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-filtered DirectoryEmployee persistence.
 */
public interface DirectoryEmployeeRepository extends JpaRepository<DirectoryEmployee, Long>
{
    /**
     * Lists records of a data source.
     *
     * @param sourceId the data source
     * @return its records
     */
    List<DirectoryEmployee> findBySourceId(long sourceId);
    /**
     * Resolves a stable binding using the employee identifier.
     *
     * @param sourceId the data source
     * @param userId stable provider identifier
     * @return the cached employee
     */
    Optional<DirectoryEmployee> findBySourceIdAndUserId(long sourceId, String userId);

    /**
     * Looks up candidate employees by a trusted mailbox.
     *
     * @param sourceId the data source
     * @param email canonical login mailbox
     * @return active matches; only a unique result can be linked
     */
    List<DirectoryEmployee> findBySourceIdAndEmailAndPresentInDirectoryTrue(long sourceId, String email);
}
