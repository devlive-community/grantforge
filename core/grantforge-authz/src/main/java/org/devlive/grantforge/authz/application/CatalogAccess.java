// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/**
 * The catalog's tenant boundary. The resource catalog is shared by all tenants, so only accounts of the platform
 * tenant change it; who may read or change it beyond that is a matter of permissions, which the API checks. Must be
 * called with the actor's tenant bound.
 */
@Component
public final class CatalogAccess
{
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final TransactionTemplate transactions;

    /**
     * Creates the check.
     *
     * @param accounts user accounts of the bound tenant
     * @param tenants tenants, to tell the platform tenant apart
     * @param transactionManager opens transactions
     */
    public CatalogAccess(UserAccountRepository accounts, TenantRepository tenants, PlatformTransactionManager transactionManager)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.tenants = requireNonNull(tenants, "tenants");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Requires an account of the platform tenant, the only tenant that changes the shared catalog.
     *
     * @param actorId the account asking
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} otherwise
     */
    public void requireEditor(long actorId)
    {
        boolean platform = Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(actorId)
                .map(UserAccount::getTenantId).flatMap(tenants::findById).map(Tenant::isPlatform).orElse(false)));
        if (!platform) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " is not of the platform tenant");
        }
    }
}
