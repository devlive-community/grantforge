// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.UserResourceGrantRepository;
import org.devlive.grantforge.identity.application.IdentityDeleted;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Removes direct grants when an account is deleted. */
@Component
public final class DirectGrantCleanup
{
    private final UserResourceGrantRepository grants;
    private final AuthorizationChanges changes;
    private final TransactionTemplate transactions;

    /**
     * Creates the cleanup listener.
     *
     * @param grants direct grants
     * @param changes authorization versions
     * @param manager transaction manager
     */
    public DirectGrantCleanup(UserResourceGrantRepository grants, AuthorizationChanges changes, PlatformTransactionManager manager)
    {
        this.grants = grants;
        this.changes = changes;
        this.transactions = new TransactionTemplate(manager);
    }

    /**
     * Handles an identity deletion.
     *
     * @param event identity deleted
     */
    @EventListener
    public void deleted(IdentityDeleted event)
    {
        if (event.kind() == IdentityDeleted.Kind.ACCOUNT) {
            transactions.executeWithoutResult(status -> {
                grants.deleteByAccountId(event.id());
                changes.currentTenant();
            });
        }
    }
}
