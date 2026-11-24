// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Identity sources of the current tenant; call as system to look across tenants. */
public interface IdentitySourceRepository
        extends JpaRepository<IdentitySource, Long>
{
    /**
     * Finds a source by its code.
     *
     * @param code the code
     * @return the source
     */
    Optional<IdentitySource> findByCode(String code);

    /**
     * Lists the sources in creation order.
     *
     * @return the sources
     */
    List<IdentitySource> findAllByOrderByIdAsc();

    /**
     * Lists the enabled sources of a type in creation order.
     *
     * @param type the type
     * @return the sources
     */
    List<IdentitySource> findByTypeAndEnabledTrueOrderByIdAsc(IdentitySourceType type);
    /**
     * Serializes OIDC account binding for this identity source.
     * @param id identity source ID
     * @return the locked source
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IdentitySource s where s.id = :id")
    Optional<IdentitySource> lockById(@Param("id") long id);
}
