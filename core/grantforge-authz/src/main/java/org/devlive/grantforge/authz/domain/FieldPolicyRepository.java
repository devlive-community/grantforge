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

/** Persistence of {@link FieldPolicy}s of the bound tenant. */
public interface FieldPolicyRepository
        extends JpaRepository<FieldPolicy, Long>
{
    /**
     * Returns a role's field policies by entity and field.
     *
     * @param roleId the role
     * @return the policies
     */
    List<FieldPolicy> findByRoleIdOrderByEntityCodeAscFieldCodeAsc(long roleId);

    /**
     * Returns the field policies of some roles.
     *
     * @param roleIds the roles
     * @return their policies
     */
    List<FieldPolicy> findByRoleIdIn(Collection<Long> roleIds);

    /**
     * Removes a role's field policies, as deleting the role or replacing its policies does.
     *
     * @param roleId the role
     * @return how many were removed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FieldPolicy p where p.roleId = :roleId")
    int removeRole(@Param("roleId") long roleId);
}
