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
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.TenantStatus;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Self-registration (D-22, D-36): when {@code grantforge.security.registration-enabled} is on, visitors create an
 * ordinary account in the tenant named by {@code grantforge.security.registration-tenant} (the setup tenant by
 * default). Registered accounts are never system accounts and get no permissions of their own.
 */
@Service
public final class RegistrationService
{
    private final SecurityProperties security;
    private final String tenantCode;
    private final TenantRepository tenants;
    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param security security settings (whether registration is enabled)
     * @param tenantCode code of the tenant new accounts join
     * @param tenants tenants
     * @param accounts user accounts
     * @param passwords checks and hashes passwords
     * @param audit records registrations
     * @param transactionManager opens transactions
     * @param clock source of the current time
     */
    public RegistrationService(SecurityProperties security,
            @Value("${grantforge.security.registration-tenant:" + SetupService.DEFAULT_TENANT_CODE + "}") String tenantCode,
            TenantRepository tenants, UserAccountRepository accounts, PasswordService passwords, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.security = requireNonNull(security, "security");
        this.tenantCode = requireNonNull(tenantCode, "tenantCode").trim().toLowerCase(Locale.ROOT);
        this.tenants = requireNonNull(tenants, "tenants");
        this.accounts = requireNonNull(accounts, "accounts");
        this.passwords = requireNonNull(passwords, "passwords");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Creates an account for a visitor, who may sign in right away.
     *
     * @param username the login name, unique across tenants
     * @param password the password
     * @param displayName the display name, if any
     * @return the login name as stored
     * @throws GrantForgeException {@link IdentityErrorCode#REGISTRATION_CLOSED},
     *         {@link IdentityErrorCode#USERNAME_TAKEN}, a password policy error, or
     *         {@link CommonErrorCode#BAD_REQUEST} for an invalid value
     */
    public String register(@Nullable String username, @Nullable String password, @Nullable String displayName)
    {
        Tenant tenant = openTenant();
        String hash = passwords.hashNew(password, username);
        UserAccount account;
        try {
            account = UserAccount.create(String.valueOf(username), hash, clock.instant()).withDisplayName(displayName);
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }
        if (TenantContext.callAsSystem(() -> accounts.findByUsernameNorm(account.getUsernameNorm())).isPresent()) {
            throw taken(account.getUsername(), null);
        }
        long tenantId = tenant.requireId();
        try {
            TenantContext.runInTenant(tenantId, () -> transactions.executeWithoutResult(status -> accounts.saveAndFlush(account)));
        }
        catch (DataIntegrityViolationException race) {
            throw taken(account.getUsername(), race);
        }
        audit.record(new AuditRecord(AuditAction.USER_REGISTERED, AuditOutcome.SUCCESS, tenantId, account.requireId(),
                account.getUsername(), Long.toString(account.requireId()), null));
        return account.getUsername();
    }

    private Tenant openTenant()
    {
        if (!security.registrationEnabled()) {
            throw closed("self-registration is disabled");
        }
        Tenant tenant = tenants.findByCode(tenantCode).orElseThrow(() -> closed("no tenant " + tenantCode));
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw closed("tenant " + tenantCode + " is suspended");
        }
        return tenant;
    }

    private static GrantForgeException closed(String detail)
    {
        return new GrantForgeException(IdentityErrorCode.REGISTRATION_CLOSED, detail);
    }

    private static GrantForgeException taken(String username, @Nullable Throwable cause)
    {
        return new GrantForgeException(IdentityErrorCode.USERNAME_TAKEN, "user name taken", cause, username);
    }
}
