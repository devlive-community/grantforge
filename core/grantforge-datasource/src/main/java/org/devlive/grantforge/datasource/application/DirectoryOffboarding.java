// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.datasource.domain.DataSource;
import org.devlive.grantforge.datasource.domain.DirectoryAccount;
import org.devlive.grantforge.datasource.domain.DirectoryAccountRepository;
import org.devlive.grantforge.identity.application.AccountAccessEnded;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;

/** Applies confirmed departures for the OIDC accounts managed by the source. */
@Component
final class DirectoryOffboarding
{
    private final DirectoryAccountRepository bindings;
    private final UserAccountRepository accounts;
    private final ExternalIdentityRepository identities;
    private final ConsoleSessionService sessions;
    private final ApplicationEventPublisher events;
    private final AuditLog audit;

    DirectoryOffboarding(DirectoryAccountRepository bindings, UserAccountRepository accounts, ExternalIdentityRepository identities,
                          ConsoleSessionService sessions, ApplicationEventPublisher events, AuditLog audit)
    {
        this.bindings = bindings;
        this.accounts = accounts;
        this.identities = identities;
        this.sessions = sessions;
        this.events = events;
        this.audit = audit;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // Each account has its own audit record and revocation event.
    void apply(DataSource source, Set<String> departed, @Nullable Long actor)
    {
        if (departed.isEmpty()) {
            return;
        }
        long tenant = TenantContext.requireTenantId();
        var affected = bindings.findBySourceId(source.requireId()).stream()
                .filter(link -> link.getEmployeeId() != null && departed.contains(link.getEmployeeId()))
                .sorted(Comparator.comparingLong(DirectoryAccount::getAccountId)).toList();
        for (DirectoryAccount link : affected) {
            long id = link.getAccountId();
            if (identities.findByAccountId(id).filter(identity -> identity.getSourceId() == source.getIdentitySourceId()).isEmpty()) {
                continue;
            }
            var account = accounts.lockForAuthentication(id, tenant).orElse(null);
            if (account == null || account.isSystemAccount()) {
                continue;
            }
            if (account.getStatus() != AccountStatus.DISABLED) {
                account.disable();
                audit.recordWithChange(new AuditRecord(AuditAction.USER_DISABLED, AuditOutcome.SUCCESS, tenant, actor, null,
                        Long.toString(id), "confirmed employee departure from directory " + source.getCode()));
            }
            events.publishEvent(new AccountAccessEnded(id));
            sessions.revokeAll(id);
        }
    }
}
