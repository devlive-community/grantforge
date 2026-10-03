// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence of {@link Resource}s. */
public interface ResourceRepository
        extends JpaRepository<Resource, Long>
{
    /**
     * Returns an application's whole tree, parents before children and siblings in order.
     *
     * @param applicationId the application
     * @return the resources
     */
    @Query("select r from Resource r where r.applicationId = :applicationId order by r.depth, r.sortOrder, r.name, r.id")
    List<Resource> findTree(@Param("applicationId") long applicationId);

    /**
     * Returns the children of a resource, or an application's top-level resources, in order.
     *
     * @param applicationId the application
     * @param parentId the parent, or {@code null} for the top level
     * @return the children
     */
    @Query("select r from Resource r where r.applicationId = :applicationId and ((:parentId is null and r.parentId is null)"
            + " or r.parentId = :parentId) order by r.sortOrder, r.name, r.id")
    List<Resource> findChildren(@Param("applicationId") long applicationId, @Param("parentId") @Nullable Long parentId);

    /**
     * Finds a resource of an application by its code.
     *
     * @param applicationId the application
     * @param code the code
     * @return the resource, if any
     */
    Optional<Resource> findByApplicationIdAndCode(long applicationId, String code);

    /**
     * Finds the resources of a type with some codes, in every application.
     *
     * @param type the type
     * @param codes the codes
     * @return the resources
     */
    List<Resource> findByTypeAndCodeIn(ResourceType type, Collection<String> codes);

    /**
     * Finds the resources of a type in every application, by code.
     *
     * @param type the type
     * @return the resources
     */
    List<Resource> findByTypeOrderByCodeAsc(ResourceType type);

    /**
     * Tells whether a resource has children.
     *
     * @param parentId the resource
     * @return {@code true} if any resource names it as parent
     */
    boolean existsByParentId(long parentId);

    /**
     * Tells whether an application has resources.
     *
     * @param applicationId the application
     * @return {@code true} if it has any
     */
    boolean existsByApplicationId(long applicationId);

    /**
     * Counts the resources of every application that has any.
     *
     * @return one count per application
     */
    @Query("select new org.devlive.grantforge.authz.domain.ResourceCount(r.applicationId, count(r)) from Resource r"
            + " group by r.applicationId")
    List<ResourceCount> countByApplication();

    /**
     * Returns the deepest level within a subtree.
     *
     * @param pathPrefix the subtree's root path followed by {@code %}
     * @return the deepest depth
     */
    @Query("select max(r.depth) from Resource r where r.path like :pathPrefix")
    int maxDepthBelow(@Param("pathPrefix") String pathPrefix);

    /**
     * Moves a subtree: replaces the path prefix of every resource in it and shifts their depth.
     *
     * @param oldPrefix the subtree root's current path
     * @param oldPattern {@code oldPrefix} followed by {@code %}
     * @param newPrefix the subtree root's new path
     * @param cut the 1-based position in each path where the part below the old prefix starts
     * @param depthShift how many levels the subtree moves down (negative: up)
     * @return the number of moved resources
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Resource r set r.path = concat(:newPrefix, substring(r.path, :cut)), r.depth = r.depth + :depthShift"
            + " where r.path like :oldPattern and substring(r.path, 1, length(:oldPrefix)) = :oldPrefix")
    int moveSubtree(@Param("oldPrefix") String oldPrefix, @Param("oldPattern") String oldPattern,
            @Param("newPrefix") String newPrefix, @Param("cut") int cut, @Param("depthShift") int depthShift);

    /**
     * Sets the parent of a resource (its path is rewritten by {@link #moveSubtree}).
     *
     * @param id the resource
     * @param parentId the new parent, or {@code null} for the top level
     * @return the number of updated resources
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Resource r set r.parentId = :parentId where r.id = :id")
    int reparent(@Param("id") long id, @Param("parentId") @Nullable Long parentId);
}
