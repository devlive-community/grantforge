// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Persistence of {@link GroupMember}s; queries are filtered to the bound tenant. */
public interface GroupMemberRepository
        extends JpaRepository<GroupMember, Long>
{
    /**
     * Lists a group's members whose login name, display name or e-mail address contains a text, by login name.
     *
     * @param groupId the group
     * @param pattern a lowercase SQL {@code LIKE} pattern such as {@code %ali%}
     * @param page the page
     * @return the members
     */
    @Query(value = "select new org.devlive.grantforge.identity.domain.MemberRow(a.id, a.username, a.displayName,"
            + " a.email, m.createdAt) from GroupMember m join UserAccount a on a.id = m.accountId"
            + " where m.groupId = :groupId and (a.usernameNorm like :pattern or lower(a.displayName) like :pattern"
            + " or lower(a.email) like :pattern) order by a.usernameNorm, a.id",
            countQuery = "select count(m) from GroupMember m join UserAccount a on a.id = m.accountId"
                    + " where m.groupId = :groupId and (a.usernameNorm like :pattern or lower(a.displayName) like :pattern"
                    + " or lower(a.email) like :pattern)")
    Page<MemberRow> findMembers(@Param("groupId") long groupId, @Param("pattern") String pattern, Pageable page);

    /**
     * Returns which of some accounts already belong to a group (at most 1000 IDs per call).
     *
     * @param groupId the group
     * @param accountIds the accounts
     * @return the IDs of the accounts that are members
     */
    @Query("select m.accountId from GroupMember m where m.groupId = :groupId and m.accountId in :accountIds")
    List<Long> findMemberIds(@Param("groupId") long groupId, @Param("accountIds") Collection<Long> accountIds);

    /**
     * Removes some accounts from a group (at most 1000 IDs per call).
     *
     * @param groupId the group
     * @param accountIds the accounts
     * @return the number of removed memberships
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from GroupMember m where m.groupId = :groupId and m.accountId in :accountIds")
    int removeMembers(@Param("groupId") long groupId, @Param("accountIds") Collection<Long> accountIds);

    /**
     * Removes every member of a group.
     *
     * @param groupId the group
     * @return the number of removed memberships
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from GroupMember m where m.groupId = :groupId")
    int removeAll(@Param("groupId") long groupId);

    /**
     * Returns the groups an account belongs to.
     *
     * @param accountId the account
     * @return the memberships
     */
    List<GroupMember> findByAccountId(long accountId);

    /**
     * Returns the accounts belonging to any of several groups.
     *
     * @param groupIds the groups
     * @return the accounts, each once
     */
    @Query("select distinct m.accountId from GroupMember m where m.groupId in :groupIds")
    List<Long> findAccountIdsByGroupIds(@Param("groupIds") Collection<Long> groupIds);
}
