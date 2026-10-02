// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Persistence of {@link RoleGrant}s; queries are filtered to the bound tenant. */
public interface RoleGrantRepository
        extends JpaRepository<RoleGrant, Long>
{
    /**
     * Returns a role's grants.
     *
     * @param roleId the role
     * @return its grants
     */
    List<RoleGrant> findByRoleId(long roleId);

    /**
     * Returns the grants of several roles.
     *
     * @param roleIds the roles
     * @return their grants
     */
    List<RoleGrant> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * Removes every grant of a role.
     *
     * @param roleId the role
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RoleGrant g where g.roleId = :roleId")
    int removeRole(@Param("roleId") long roleId);

    /**
     * Counts the grants of a resource; with the tenant filter off ({@code TenantContext.callAsSystem}) across all
     * tenants.
     *
     * @param resourceId the resource
     * @return how many grants refer to it
     */
    long countByResourceId(long resourceId);
}
