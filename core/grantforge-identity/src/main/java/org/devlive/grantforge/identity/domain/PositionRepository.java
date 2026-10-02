// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.secured.ScopedRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence of {@link Position}s; queries are filtered to the bound tenant. */
public interface PositionRepository
        extends ScopedRepository<Position, Long>
{
    /**
     * Finds a position by its code.
     *
     * @param code the lowercase code
     * @return the position, if any
     */
    Optional<Position> findByCode(String code);

    /**
     * Reads positions with their holder counts.
     *
     * @param ids the positions, at most one IN clause's worth
     * @return the positions, in no particular order
     */
    @Query("select new org.devlive.grantforge.identity.domain.PositionRow(p.id, p.code, p.name, p.description,"
            + " p.sortOrder, (select count(a) from AccountPosition a where a.positionId = p.id)) from Position p"
            + " where p.id in :ids")
    List<PositionRow> rows(@Param("ids") Collection<Long> ids);
}
