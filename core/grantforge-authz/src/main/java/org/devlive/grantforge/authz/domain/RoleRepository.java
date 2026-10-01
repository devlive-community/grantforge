// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Persistence of {@link Role}s; queries are filtered to the bound tenant. */
public interface RoleRepository
        extends JpaRepository<Role, Long>
{
    /**
     * Finds a role by its code.
     *
     * @param code the lowercase code
     * @return the role, if any
     */
    Optional<Role> findByCode(String code);

    /**
     * Lists roles whose code or name contains a text, system roles first, then by name.
     *
     * @param pattern a lowercase {@code LIKE} pattern, {@code %} for all roles
     * @return the roles
     */
    @Query("select r from Role r where lower(r.code) like :pattern or lower(r.name) like :pattern"
            + " order by r.type desc, r.name, r.id")
    List<Role> search(@Param("pattern") String pattern);
}
