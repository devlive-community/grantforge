// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Persistence of {@link Application}s. */
public interface ApplicationRepository
        extends JpaRepository<Application, Long>
{
    /**
     * Finds an application by its code.
     *
     * @param code the lowercase code
     * @return the application, if any
     */
    Optional<Application> findByCode(String code);

    /**
     * Returns every application, built-in ones first, then by name.
     *
     * @return the applications
     */
    @Query("select a from Application a order by a.builtin desc, a.name, a.id")
    List<Application> findOrdered();
}
