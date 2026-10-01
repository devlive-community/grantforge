// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Platform administration of tenants. Only platform administrators (system accounts of the platform tenant) may
 * use it; tenant administrators manage their own tenant elsewhere and never see other tenants. Every method must
 * be called with the actor's tenant bound.
 */
@Service
public final class TenantService
        implements PlatformAdministrators
{
    private final TenantRepository tenants;
    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final ConsoleSessionService sessions;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param tenants tenants
     * @param accounts user accounts
     * @param passwords checks and hashes the first administrator's password
     * @param sessions ends the sessions of a suspended tenant
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param clock source of the current time
     */
    public TenantService(TenantRepository tenants, UserAccountRepository accounts, PasswordService passwords,
            ConsoleSessionService sessions, AuditLog audit, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.tenants = requireNonNull(tenants, "tenants");
        this.accounts = requireNonNull(accounts, "accounts");
        this.passwords = requireNonNull(passwords, "passwords");
        this.sessions = requireNonNull(sessions, "sessions");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns whether an account administers the platform.
     *
     * @param accountId the account, in the bound tenant
     * @return {@code true} for system accounts of the platform tenant
     */
    @Override
    public boolean isPlatformAdministrator(long accountId)
    {
        return Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(accountId)
                .filter(UserAccount::isSystemAccount)
                .map(UserAccount::getTenantId)
                .flatMap(tenants::findById)
                .map(Tenant::isPlatform)
                .orElse(false)));
    }

    /**
     * Lists tenants whose code or name contains a text, the platform tenant first.
     *
     * @param actorId the account asking
     * @param text the text to look for, or {@code null} for every tenant
     * @param page the page
     * @return the tenants
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} unless the actor administers the platform
     */
    public PageResult<TenantSummary> list(long actorId, @Nullable String text, PageQuery page)
    {
        requirePlatformAdministrator(actorId);
        String needle = Strings.blankToNull(text);
        String pattern = needle == null ? "%" : "%" + withoutWildcards(needle.toLowerCase(Locale.ROOT)) + "%";
        Page<Tenant> found = requireNonNull(transactions.execute(status ->
                tenants.search(pattern, PageRequest.of(page.page() - 1, page.size()))));
        Map<Long, Long> counts = accountCounts(found.getContent().stream().map(Tenant::requireId).toList());
        return new PageResult<>(found.getContent().stream()
                .map(tenant -> summary(tenant, counts.getOrDefault(tenant.requireId(), 0L))).toList(),
                page.page(), page.size(), found.getTotalElements());
    }

    /**
     * Returns one tenant.
     *
     * @param actorId the account asking
     * @param tenantId the tenant
     * @return the tenant
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} unless the actor administers the platform,
     *         or {@link CommonErrorCode#NOT_FOUND}
     */
    public TenantSummary find(long actorId, long tenantId)
    {
        requirePlatformAdministrator(actorId);
        return summary(require(tenantId));
    }

    /**
     * Creates a tenant and its first administrator, who must choose a new password at the first sign-in.
     *
     * @param actorId the account asking
     * @param command the tenant and administrator
     * @return the new tenant
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} unless the actor administers the platform,
     *         {@link IdentityErrorCode#TENANT_CODE_TAKEN}, {@link IdentityErrorCode#USERNAME_TAKEN}, a password policy
     *         error, or {@link CommonErrorCode#BAD_REQUEST} for an invalid value
     */
    public TenantSummary create(long actorId, TenantCommand command)
    {
        requireNonNull(command, "command");
        requirePlatformAdministrator(actorId);
        String passwordHash = passwords.hashNew(command.adminPassword(), command.adminUsername());
        Tenant tenant;
        UserAccount administrator;
        try {
            tenant = Tenant.create(String.valueOf(command.code()), String.valueOf(command.name()));
            administrator = UserAccount.create(String.valueOf(command.adminUsername()), passwordHash, clock.instant())
                    .withDisplayName(command.adminDisplayName())
                    .markSystemAccount();
            administrator.requirePasswordChange();
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }
        requireFree(tenant, administrator);
        try {
            // The tenant ID is preassigned, so the administrator can be created in the new tenant right away.
            TenantContext.runInTenant(tenant.requireId(), () -> transactions.executeWithoutResult(status -> {
                tenants.saveAndFlush(tenant);
                accounts.save(administrator);
            }));
        }
        catch (DataIntegrityViolationException race) {
            requireFree(tenant, administrator);
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "tenant created concurrently", race);
        }
        record(AuditAction.TENANT_CREATED, actorId, tenant);
        return summary(tenant, 1);
    }

    /**
     * Renames a tenant.
     *
     * @param actorId the account asking
     * @param tenantId the tenant
     * @param name the new name
     * @return the tenant
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link CommonErrorCode#BAD_REQUEST} for an invalid name
     */
    public TenantSummary rename(long actorId, long tenantId, @Nullable String name)
    {
        requirePlatformAdministrator(actorId);
        Tenant tenant = change(tenantId, found -> {
            try {
                found.rename(String.valueOf(name));
            }
            catch (IllegalArgumentException invalid) {
                throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
            }
        });
        record(AuditAction.TENANT_UPDATED, actorId, tenant);
        return summary(tenant);
    }

    /**
     * Suspends a tenant: its accounts can no longer sign in and their sessions end.
     *
     * @param actorId the account asking
     * @param tenantId the tenant
     * @return the tenant
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link IdentityErrorCode#PLATFORM_TENANT_PROTECTED}
     */
    public TenantSummary suspend(long actorId, long tenantId)
    {
        requirePlatformAdministrator(actorId);
        Tenant tenant = change(tenantId, found -> {
            if (found.isPlatform()) {
                throw new GrantForgeException(IdentityErrorCode.PLATFORM_TENANT_PROTECTED, "platform tenant " + tenantId);
            }
            found.suspend();
        });
        TenantContext.runInTenant(tenantId, () -> {
            List<Long> members = requireNonNull(transactions.execute(status -> accounts.findAll().stream()
                    .map(UserAccount::requireId).toList()));
            members.forEach(sessions::revokeAll);
        });
        record(AuditAction.TENANT_SUSPENDED, actorId, tenant);
        return summary(tenant);
    }

    /**
     * Lets a suspended tenant's accounts sign in again.
     *
     * @param actorId the account asking
     * @param tenantId the tenant
     * @return the tenant
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public TenantSummary activate(long actorId, long tenantId)
    {
        requirePlatformAdministrator(actorId);
        Tenant tenant = change(tenantId, Tenant::activate);
        record(AuditAction.TENANT_ACTIVATED, actorId, tenant);
        return summary(tenant);
    }

    private void requirePlatformAdministrator(long actorId)
    {
        if (!isPlatformAdministrator(actorId)) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " does not administer the platform");
        }
    }

    private void requireFree(Tenant tenant, UserAccount administrator)
    {
        if (tenants.findByCode(tenant.getCode()).isPresent()) {
            throw new GrantForgeException(IdentityErrorCode.TENANT_CODE_TAKEN, "tenant code taken", tenant.getCode());
        }
        if (TenantContext.callAsSystem(() -> accounts.findByUsernameNorm(administrator.getUsernameNorm())).isPresent()) {
            throw new GrantForgeException(IdentityErrorCode.USERNAME_TAKEN, "user name taken", administrator.getUsername());
        }
    }

    private Tenant require(long tenantId)
    {
        return tenants.findById(tenantId)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no tenant " + tenantId));
    }

    private Tenant change(long tenantId, Consumer<Tenant> change)
    {
        return requireNonNull(transactions.execute(status -> {
            Tenant tenant = require(tenantId);
            change.accept(tenant);
            return tenant;
        }));
    }

    private Map<Long, Long> accountCounts(List<Long> tenantIds)
    {
        if (tenantIds.isEmpty()) {
            return Map.of();
        }
        // Accounts are tenant-scoped; counting across tenants needs system context.
        return TenantContext.callAsSystem(() -> requireNonNull(transactions.execute(status ->
                accounts.countByTenant(tenantIds).stream().collect(Collectors.toMap(
                        UserAccountRepository.TenantAccounts::getTenantId,
                        UserAccountRepository.TenantAccounts::getAccounts)))));
    }

    private TenantSummary summary(Tenant tenant)
    {
        return summary(tenant, accountCounts(List.of(tenant.requireId())).getOrDefault(tenant.requireId(), 0L));
    }

    private static TenantSummary summary(Tenant tenant, long accounts)
    {
        return new TenantSummary(tenant.requireId(), tenant.getCode(), tenant.getName(), tenant.getStatus(),
                tenant.isPlatform(), accounts, requireNonNull(tenant.getCreatedAt(), "createdAt"));
    }

    private void record(AuditAction action, long actorId, Tenant tenant)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, tenant.requireId(), actorId, null,
                Long.toString(tenant.requireId()), null));
    }

    /** Drops the {@code LIKE} wildcards from user input, so searching for "50%" cannot match everything. */
    private static String withoutWildcards(String text)
    {
        return text.replace("%", "").replace("_", "");
    }
}
