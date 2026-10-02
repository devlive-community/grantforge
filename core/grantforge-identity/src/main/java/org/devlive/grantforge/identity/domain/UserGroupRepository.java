// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.secured.ScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence of {@link UserGroup}s; queries are filtered to the bound tenant. */
public interface UserGroupRepository
        extends ScopedRepository<UserGroup, Long>
{
    /**
     * Finds a group by its code.
     *
     * @param code the lowercase code
     * @return the group, if any
     */
    Optional<UserGroup> findByCode(String code);

    /**
     * Finds groups whose code or name contains a text, with their member counts, by name.
     *
     * @param pattern a lowercase SQL {@code LIKE} pattern such as {@code %ops%}
     * @param page the page
     * @return the groups
     */
    @Query(value = "select new org.devlive.grantforge.identity.domain.GroupRow(g.id, g.code, g.name, g.description,"
            + " (select count(m) from GroupMember m where m.groupId = g.id), g.createdAt)"
            + " from UserGroup g where g.code like :pattern or lower(g.name) like :pattern order by g.name, g.id",
            countQuery = "select count(g) from UserGroup g where g.code like :pattern or lower(g.name) like :pattern")
    Page<GroupRow> search(@Param("pattern") String pattern, Pageable page);

    /**
     * Reads groups with their member counts.
     *
     * @param ids the groups, at most one IN clause's worth
     * @return the groups, in no particular order
     */
    @Query("select new org.devlive.grantforge.identity.domain.GroupRow(g.id, g.code, g.name, g.description,"
            + " (select count(m) from GroupMember m where m.groupId = g.id), g.createdAt) from UserGroup g where g.id in :ids")
    List<GroupRow> rows(@Param("ids") Collection<Long> ids);
}
