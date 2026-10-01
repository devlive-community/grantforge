// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Persistence of {@link UserAccount}s. Queries are filtered to the bound tenant; sign-in looks accounts up
 * in system context because login names are unique across tenants.
 */
public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long>
{
    /**
     * Finds an account by its canonical login name.
     *
     * @param usernameNorm the result of {@link UserAccount#normalize(String)}
     * @return the account, if any
     */
    Optional<UserAccount> findByUsernameNorm(String usernameNorm);
}
