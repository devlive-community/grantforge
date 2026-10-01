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

import java.util.List;
import java.util.Optional;

/** Persistence of {@link Position}s; queries are filtered to the bound tenant. */
public interface PositionRepository
        extends JpaRepository<Position, Long>
{
    /**
     * Finds a position by its code.
     *
     * @param code the lowercase code
     * @return the position, if any
     */
    Optional<Position> findByCode(String code);

    /**
     * Finds positions whose code or name contains a text, with their holder counts, in list order.
     *
     * @param pattern a lowercase SQL {@code LIKE} pattern such as {@code %manager%}
     * @param page the page
     * @return the positions
     */
    @Query(value = "select new org.devlive.grantforge.identity.domain.PositionRow(p.id, p.code, p.name, p.description,"
            + " p.sortOrder, (select count(a) from AccountPosition a where a.positionId = p.id)) from Position p"
            + " where p.code like :pattern or lower(p.name) like :pattern order by p.sortOrder, p.name, p.id",
            countQuery = "select count(p) from Position p where p.code like :pattern or lower(p.name) like :pattern")
    Page<PositionRow> search(@Param("pattern") String pattern, Pageable page);

    /**
     * Returns every position in list order, for pickers.
     *
     * @return the positions
     */
    @Query("select p from Position p order by p.sortOrder, p.name, p.id")
    List<Position> findAllInOrder();
}
