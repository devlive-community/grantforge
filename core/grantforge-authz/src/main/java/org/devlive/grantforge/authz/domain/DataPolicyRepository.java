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

/** The data policies of the bound tenant's roles. */
public interface DataPolicyRepository
        extends JpaRepository<DataPolicy, Long>
{
    /**
     * Returns the policies of a role, by entity and then id.
     *
     * @param roleId the role
     * @return the policies
     */
    List<DataPolicy> findByRoleIdOrderByEntityCodeAscIdAsc(long roleId);

    /**
     * Returns the policies of roles.
     *
     * @param roleIds the roles, at most 1000
     * @return the policies
     */
    List<DataPolicy> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * Removes every policy of a role.
     *
     * @param roleId the role
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DataPolicy p where p.roleId = :roleId")
    int removeRole(@Param("roleId") long roleId);
}
