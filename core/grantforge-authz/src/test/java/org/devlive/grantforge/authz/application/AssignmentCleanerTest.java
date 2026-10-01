// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.identity.application.IdentityDeleted;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentCleanerTest
{
    @Test
    void mapsEveryKindToItsSubjectType()
    {
        RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(status);
        AssignmentCleaner cleaner = new AssignmentCleaner(assignments, transactions);

        cleaner.deleted(new IdentityDeleted(IdentityDeleted.Kind.ACCOUNT, 1));
        cleaner.deleted(new IdentityDeleted(IdentityDeleted.Kind.GROUP, 2));
        cleaner.deleted(new IdentityDeleted(IdentityDeleted.Kind.ORG_UNIT, 3));
        cleaner.deleted(new IdentityDeleted(IdentityDeleted.Kind.POSITION, 4));

        verify(assignments).removeSubject(SubjectType.USER, 1);
        verify(assignments).removeSubject(SubjectType.GROUP, 2);
        verify(assignments).removeSubject(SubjectType.ORG_UNIT, 3);
        verify(assignments).removeSubject(SubjectType.POSITION, 4);
    }
}
