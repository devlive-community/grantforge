// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Recovery codes of the bound tenant's accounts. */
public interface MfaRecoveryCodeRepository
        extends JpaRepository<MfaRecoveryCode, Long>
{
    /**
     * Lists an account's codes.
     *
     * @param accountId the account
     * @return the codes, used or not
     */
    List<MfaRecoveryCode> findByAccountId(long accountId);

    /**
     * Deletes an account's codes, as when it gets new ones or turns two-step sign-in off.
     *
     * @param accountId the account
     * @return how many went
     */
    @Modifying
    @Query("delete from MfaRecoveryCode c where c.accountId = :account")
    int deleteByAccount(@Param("account") long accountId);
}
