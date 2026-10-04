// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/** Access reviews of the bound tenant; call as system to look across tenants. */
public interface AccessReviewRepository
        extends JpaRepository<AccessReview, Long>
{
    /**
     * Lists the reviews by name.
     *
     * @return the reviews
     */
    List<AccessReview> findAllByOrderByNameAscIdAsc();

    /**
     * Lists the enabled reviews whose next round's time has come.
     *
     * @param now the current time
     * @return the reviews
     */
    List<AccessReview> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);
}
