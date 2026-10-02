// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Persistence of {@link ApiEndpoint}s. */
public interface ApiEndpointRepository
        extends JpaRepository<ApiEndpoint, Long>
{
    /**
     * Returns every endpoint ever found, by path and method.
     *
     * @return the endpoints
     */
    @Query("select e from ApiEndpoint e order by e.pathPattern, e.httpMethod")
    List<ApiEndpoint> findOrdered();
}
