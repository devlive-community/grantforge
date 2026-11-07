// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.UserResourceGrantRepository;
import org.devlive.grantforge.identity.application.IdentityDeleted;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DirectGrantCleanupTest
{
    @Test
    void removesOnlyDeletedAccountsGrantsAndInvalidatesAuthorization()
    {
        UserResourceGrantRepository grants = mock(UserResourceGrantRepository.class);
        AuthorizationChanges changes = mock(AuthorizationChanges.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        DirectGrantCleanup cleaner = new DirectGrantCleanup(grants, changes, transactions);
        cleaner.deleted(new IdentityDeleted(IdentityDeleted.Kind.GROUP, 4));
        cleaner.deleted(new IdentityDeleted(IdentityDeleted.Kind.ACCOUNT, 5));
        verify(grants).deleteByAccountId(5);
        verify(changes).currentTenant();
        verifyNoMoreInteractions(grants, changes);
    }
}
