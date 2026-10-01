// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.identity.application.IdentityDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/** Drops the role assignments of accounts, groups, departments and positions once they are deleted. */
@Component
public final class AssignmentCleaner
{
    private final RoleAssignmentRepository assignments;
    private final TransactionTemplate transactions;

    /**
     * Creates the cleaner.
     *
     * @param assignments assignments of the bound tenant
     * @param transactionManager opens transactions
     */
    public AssignmentCleaner(RoleAssignmentRepository assignments, PlatformTransactionManager transactionManager)
    {
        this.assignments = requireNonNull(assignments, "assignments");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Removes the assignments of something deleted; runs with the deleting request's tenant bound.
     *
     * @param event what was deleted
     */
    @EventListener
    public void deleted(IdentityDeleted event)
    {
        SubjectType type = switch (event.kind()) {
            case ACCOUNT -> SubjectType.USER;
            case GROUP -> SubjectType.GROUP;
            case ORG_UNIT -> SubjectType.ORG_UNIT;
            case POSITION -> SubjectType.POSITION;
        };
        transactions.executeWithoutResult(status -> assignments.removeSubject(type, event.id()));
    }
}
