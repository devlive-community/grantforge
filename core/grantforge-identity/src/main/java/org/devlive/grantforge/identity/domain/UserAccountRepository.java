// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence of {@link UserAccount}s. Queries are filtered to the bound tenant; sign-in looks accounts up
 * in system context because login names are unique across tenants.
 */
public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long>, UserSearchRepository
{
    /**
     * Finds an account by its canonical login name.
     *
     * @param usernameNorm the result of {@link UserAccount#normalize(String)}
     * @return the account, if any
     */
    Optional<UserAccount> findByUsernameNorm(String usernameNorm);

    /**
     * Counts the accounts of each of some tenants; call it in system context to see every tenant.
     *
     * @param tenantIds the tenants
     * @return one entry per tenant that has accounts
     */
    @Query("select a.tenantId as tenantId, count(a) as accounts from UserAccount a where a.tenantId in :tenantIds"
            + " group by a.tenantId")
    List<TenantAccounts> countByTenant(@Param("tenantIds") Collection<Long> tenantIds);

    /** The number of accounts of one tenant. */
    interface TenantAccounts
    {
        /**
         * Returns the tenant.
         *
         * @return the tenant ID
         */
        Long getTenantId();

        /**
         * Returns the number of accounts.
         *
         * @return the count
         */
        long getAccounts();
    }
}
