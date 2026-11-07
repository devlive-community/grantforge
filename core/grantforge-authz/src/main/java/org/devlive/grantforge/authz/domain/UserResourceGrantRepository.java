// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Tenant-scoped direct user resource grants. */
public interface UserResourceGrantRepository extends JpaRepository<UserResourceGrant, Long>
{
    /**
     * Finds an account's direct grants.
     *
     * @param accountId the account
     * @return the grants
     */
    List<UserResourceGrant> findByAccountId(long accountId);
    /**
     * Removes an account's grants.
     *
     * @param accountId the account
     */
    void deleteByAccountId(long accountId);
    /**
     * Counts grants to a resource across the active scope.
     *
     * @param resourceId the resource
     * @return number of grants
     */
    long countByResourceId(long resourceId);
}
