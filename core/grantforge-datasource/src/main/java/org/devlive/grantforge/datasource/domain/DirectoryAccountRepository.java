// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-filtered DirectoryAccount persistence.
 */
public interface DirectoryAccountRepository extends JpaRepository<DirectoryAccount, Long>
{
    /**
     * Lists records of a data source.
     *
     * @param sourceId the data source
     * @return its records
     */
    List<DirectoryAccount> findBySourceId(long sourceId);
    /**
     * Finds one authenticated account's pending or stable binding.
     *
     * @param sourceId the data source
     * @param accountId the login account
     * @return its binding
     */
    Optional<DirectoryAccount> findBySourceIdAndAccountId(long sourceId, long accountId);

    /**
     * Counts competing trusted mailboxes without loading other accounts.
     *
     * @param sourceId the data source
     * @param trustedEmail canonical login mailbox
     * @return the number of matching accounts
     */
    long countBySourceIdAndTrustedEmail(long sourceId, String trustedEmail);

    /**
     * Tests whether an employee is already bound.
     *
     * @param sourceId the data source
     * @param employeeId stable provider identifier
     * @return whether a binding exists
     */
    boolean existsBySourceIdAndEmployeeId(long sourceId, String employeeId);
}
