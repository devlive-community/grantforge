// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Roles accounts of the bound tenant may ask for. */
public interface RequestableRoleRepository
        extends JpaRepository<RequestableRole, Long>
{
    /**
     * Finds the setting of a role.
     *
     * @param roleId the role
     * @return the setting, if the role is requestable
     */
    Optional<RequestableRole> findByRoleId(long roleId);
}
