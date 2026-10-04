// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

/** Searching accounts of the bound tenant; part of {@link UserAccountRepository}. */
public interface UserSearchRepository
{
    /**
     * Lists matching accounts, the newest first.
     *
     * @param criteria the filters
     * @param scope the accounts the reader may see, combined with the filters
     * @param now the current time, to tell locked accounts apart
     * @param offset how many matches to skip
     * @param limit how many matches to return at most
     * @return the accounts
     */
    List<UserRow> search(UserCriteria criteria, Specification<UserAccount> scope, Instant now, long offset, int limit);

    /**
     * Counts matching accounts.
     *
     * @param criteria the filters
     * @param scope the accounts the reader may see, combined with the filters
     * @param now the current time, to tell locked accounts apart
     * @return the number of matches
     */
    long count(UserCriteria criteria, Specification<UserAccount> scope, Instant now);
}
