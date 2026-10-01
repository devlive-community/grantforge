// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/**
 * Who may read and change the resource catalog until roles exist (M6): administrators of any tenant read it,
 * because they will grant its resources; only platform administrators change it, because it is shared by all
 * tenants. Must be called with the actor's tenant bound.
 */
@Component
public final class CatalogAccess
{
    private final UserAccountRepository accounts;
    private final PlatformAdministrators platform;
    private final TransactionTemplate transactions;

    /**
     * Creates the checks.
     *
     * @param accounts user accounts of the bound tenant
     * @param platform tells platform administrators apart
     * @param transactionManager opens transactions
     */
    public CatalogAccess(UserAccountRepository accounts, PlatformAdministrators platform,
            PlatformTransactionManager transactionManager)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.platform = requireNonNull(platform, "platform");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Requires an administrator of the actor's tenant.
     *
     * @param actorId the account asking
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} otherwise
     */
    public void requireReader(long actorId)
    {
        boolean administrator = Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(actorId)
                .map(UserAccount::isSystemAccount).orElse(false)));
        if (!administrator) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " may not read the catalog");
        }
    }

    /**
     * Requires a platform administrator.
     *
     * @param actorId the account asking
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} otherwise
     */
    public void requireEditor(long actorId)
    {
        if (!platform.isPlatformAdministrator(actorId)) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " may not change the catalog");
        }
    }
}
