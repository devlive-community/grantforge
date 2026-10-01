// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence of {@link PasswordHistory} entries. */
public interface PasswordHistoryRepository
        extends JpaRepository<PasswordHistory, Long>
{
    /**
     * Returns an account's former passwords, newest first.
     *
     * @param accountId the account
     * @param limit how many entries to return at most
     * @return the entries
     */
    List<PasswordHistory> findByAccountIdOrderByCreatedAtDescIdDesc(long accountId, Limit limit);

    /**
     * Returns all former passwords of an account, newest first, for trimming.
     *
     * @param accountId the account
     * @return the entries
     */
    List<PasswordHistory> findByAccountIdOrderByCreatedAtDescIdDesc(long accountId);
}
