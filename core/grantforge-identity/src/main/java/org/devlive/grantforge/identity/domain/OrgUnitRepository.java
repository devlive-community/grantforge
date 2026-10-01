// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Persistence of {@link OrgUnit}s; queries are filtered to the bound tenant. */
public interface OrgUnitRepository
        extends JpaRepository<OrgUnit, Long>
{
    /**
     * Returns the whole tree, parents before children and siblings in order.
     *
     * @return the units
     */
    @Query("select u from OrgUnit u order by u.depth, u.sortOrder, u.name, u.id")
    List<OrgUnit> findTree();

    /**
     * Returns the children of a unit, or the roots, in order.
     *
     * @param parentId the parent, or {@code null} for the roots
     * @return the children
     */
    @Query("select u from OrgUnit u where (:parentId is null and u.parentId is null) or u.parentId = :parentId"
            + " order by u.sortOrder, u.name, u.id")
    List<OrgUnit> findChildren(@Param("parentId") @Nullable Long parentId);

    /**
     * Finds a unit by its code.
     *
     * @param code the lowercase code
     * @return the unit, if any
     */
    Optional<OrgUnit> findByCode(String code);

    /**
     * Tells whether a unit has children.
     *
     * @param parentId the unit
     * @return {@code true} if any unit names it as parent
     */
    boolean existsByParentId(long parentId);

    /**
     * Returns the deepest level within a subtree.
     *
     * @param pathPrefix the subtree's root path followed by {@code %}
     * @return the deepest depth
     */
    @Query("select max(u.depth) from OrgUnit u where u.path like :pathPrefix")
    int maxDepthBelow(@Param("pathPrefix") String pathPrefix);

    /**
     * Moves a subtree: replaces the path prefix of every unit in it and shifts their depth.
     *
     * @param oldPrefix the subtree root's current path
     * @param oldPattern {@code oldPrefix} followed by {@code %}
     * @param newPrefix the subtree root's new path
     * @param cut the 1-based position in each path where the part below the old prefix starts
     * @param depthShift how many levels the subtree moves down (negative: up)
     * @return the number of moved units
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OrgUnit u set u.path = concat(:newPrefix, substring(u.path, :cut)), u.depth = u.depth + :depthShift"
            + " where u.path like :oldPattern and substring(u.path, 1, length(:oldPrefix)) = :oldPrefix")
    int moveSubtree(@Param("oldPrefix") String oldPrefix, @Param("oldPattern") String oldPattern,
            @Param("newPrefix") String newPrefix, @Param("cut") int cut, @Param("depthShift") int depthShift);

    /**
     * Sets the parent of a unit (its path is rewritten by {@link #moveSubtree}).
     *
     * @param id the unit
     * @param parentId the new parent, or {@code null} to make it a root
     * @return the number of updated units
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OrgUnit u set u.parentId = :parentId where u.id = :id")
    int reparent(@Param("id") long id, @Param("parentId") @Nullable Long parentId);
}
