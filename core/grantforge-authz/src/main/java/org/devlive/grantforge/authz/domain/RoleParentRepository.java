// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Inheritance links between roles of the bound tenant. */
public interface RoleParentRepository
        extends JpaRepository<RoleParent, Long>
{
    /**
     * Returns the links of a role to its parents.
     *
     * @param roleId the role
     * @return its links
     */
    List<RoleParent> findByRoleId(long roleId);

    /**
     * Removes every link from or to a role, before the role itself goes.
     *
     * @param roleId the role
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RoleParent p where p.roleId = :roleId or p.parentId = :roleId")
    void removeRole(@Param("roleId") long roleId);
}
