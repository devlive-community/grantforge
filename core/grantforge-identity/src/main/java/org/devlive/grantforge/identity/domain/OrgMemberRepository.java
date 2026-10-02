// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Persistence of {@link OrgMember}s; queries are filtered to the bound tenant. */
public interface OrgMemberRepository
        extends JpaRepository<OrgMember, Long>
{
    /**
     * Returns the memberships of an account, the primary one first.
     *
     * @param accountId the account
     * @return the memberships
     */
    @Query("select m from OrgMember m where m.accountId = :accountId order by m.primaryUnit desc, m.id")
    List<OrgMember> findByAccount(@Param("accountId") long accountId);

    /**
     * Removes every membership of an account.
     *
     * @param accountId the account
     * @return the number of removed memberships
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from OrgMember m where m.accountId = :accountId")
    int deleteByAccount(@Param("accountId") long accountId);

    /**
     * Tells whether a department has members.
     *
     * @param orgUnitId the department
     * @return {@code true} if any account belongs to it
     */
    boolean existsByOrgUnitId(long orgUnitId);

    /**
     * Returns the memberships of some accounts (at most 1000 per call).
     *
     * @param accountIds the accounts
     * @return the memberships
     */
    List<OrgMember> findByAccountIdIn(Collection<Long> accountIds);

    /**
     * Returns the accounts belonging to any of several departments.
     *
     * @param unitIds the departments
     * @return the accounts, each once
     */
    @Query("select distinct m.accountId from OrgMember m where m.orgUnitId in :unitIds")
    List<Long> findAccountIdsByUnitIds(@Param("unitIds") Collection<Long> unitIds);
}
