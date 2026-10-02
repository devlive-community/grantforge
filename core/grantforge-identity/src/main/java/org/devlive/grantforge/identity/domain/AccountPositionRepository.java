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

/** Persistence of {@link AccountPosition}s; queries are filtered to the bound tenant. */
public interface AccountPositionRepository
        extends JpaRepository<AccountPosition, Long>
{
    /**
     * Returns the positions an account holds.
     *
     * @param accountId the account
     * @return the assignments
     */
    List<AccountPosition> findByAccountId(long accountId);

    /**
     * Returns the positions some accounts hold (at most 1000 per call).
     *
     * @param accountIds the accounts
     * @return the assignments
     */
    List<AccountPosition> findByAccountIdIn(Collection<Long> accountIds);

    /**
     * Removes every position of an account.
     *
     * @param accountId the account
     * @return the number of removed assignments
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from AccountPosition a where a.accountId = :accountId")
    int removeAllOf(@Param("accountId") long accountId);

    /**
     * Removes a position from every account holding it.
     *
     * @param positionId the position
     * @return the number of removed assignments
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from AccountPosition a where a.positionId = :positionId")
    int removePosition(@Param("positionId") long positionId);

    /**
     * Lists the accounts holding a position, by login name.
     *
     * @param positionId the position
     * @param page the page
     * @return the holders
     */
    @Query(value = "select new org.devlive.grantforge.identity.domain.MemberRow(u.id, u.username, u.displayName,"
            + " u.email, a.createdAt) from AccountPosition a join UserAccount u on u.id = a.accountId"
            + " where a.positionId = :positionId order by u.usernameNorm, u.id",
            countQuery = "select count(a) from AccountPosition a where a.positionId = :positionId")
    Page<MemberRow> findHolders(@Param("positionId") long positionId, Pageable page);

    /**
     * Returns the accounts holding any of several positions.
     *
     * @param positionIds the positions
     * @return the accounts, each once
     */
    @Query("select distinct a.accountId from AccountPosition a where a.positionId in :positionIds")
    List<Long> findAccountIdsByPositionIds(@Param("positionIds") Collection<Long> positionIds);
}
