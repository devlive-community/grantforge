// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence of {@link Tenant}s; tenants are platform-level, so tenant binding does not filter them. */
public interface TenantRepository
        extends JpaRepository<Tenant, Long>
{
    /**
     * Finds a tenant by its code.
     *
     * @param code the lowercase code
     * @return the tenant, if any
     */
    Optional<Tenant> findByCode(String code);

    /**
     * Finds tenants whose code or name contains a text, the platform tenant first, then the newest.
     *
     * @param pattern a lowercase SQL {@code LIKE} pattern such as {@code %acme%}
     * @param page the page
     * @return the tenants
     */
    @Query("select t from Tenant t where lower(t.code) like :pattern or lower(t.name) like :pattern"
            + " order by t.platform desc, t.createdAt desc, t.id desc")
    Page<Tenant> search(@Param("pattern") String pattern, Pageable page);
}
