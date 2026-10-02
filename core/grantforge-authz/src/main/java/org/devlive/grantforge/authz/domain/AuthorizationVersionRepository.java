// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/** The counters of {@code gf_authz_version}. */
public interface AuthorizationVersionRepository
        extends JpaRepository<AuthorizationVersion, String>
{
    /**
     * Raises the counter of a scope.
     *
     * @param scope the scope
     * @param now the time of the change
     * @return 1, or 0 if the scope has no counter yet
     */
    @Modifying
    @Query("update AuthorizationVersion v set v.version = v.version + 1, v.updatedAt = :now where v.scope = :scope")
    int raise(@Param("scope") String scope, @Param("now") Instant now);
}
