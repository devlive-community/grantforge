// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.TenantStatus;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Checks sign-in credentials.
 *
 * <p>Login names are unique across tenants (D-23), so the account is found without a tenant; the attempt is
 * then evaluated in a transaction bound to the account's tenant. Failed attempts count towards a temporary
 * lockout and are committed even though the sign-in fails. An unknown name costs one password hash too, so
 * response times do not reveal which names exist. Disabled accounts and suspended tenants are reported only
 * after the correct password, for the same reason. Every attempt is audited, with the refusal reason.
 */
@Service
public final class AuthenticationService
{
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final PasswordService passwords;
    private final PasswordEncoder encoder;
    private final SecurityProperties.Lockout lockout;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String unknownAccountHash;
    private final AuditLog audit;

    /**
     * Creates the service.
     *
     * @param accounts user accounts
     * @param tenants tenants
     * @param passwords verifies passwords
     * @param encoder hashes the stand-in password used for unknown names
     * @param properties security settings (lockout rule)
     * @param transactionManager commits each attempt
     * @param clock source of the current time
     * @param audit records every attempt
     */
    public AuthenticationService(UserAccountRepository accounts, TenantRepository tenants, PasswordService passwords,
            PasswordEncoder encoder, SecurityProperties properties, PlatformTransactionManager transactionManager,
            Clock clock, AuditLog audit)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.tenants = requireNonNull(tenants, "tenants");
        this.passwords = requireNonNull(passwords, "passwords");
        this.encoder = requireNonNull(encoder, "encoder");
        this.lockout = requireNonNull(properties, "properties").lockout();
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
        this.unknownAccountHash = encoder.encode(UUID.randomUUID().toString());
        this.audit = requireNonNull(audit, "audit");
    }

    /**
     * Signs an account in.
     *
     * @param username the login name in any case; {@code null} never matches
     * @param password the password; {@code null} never matches
     * @return the signed-in account
     * @throws GrantForgeException {@link IdentityErrorCode#INVALID_CREDENTIALS},
     *         {@link IdentityErrorCode#ACCOUNT_LOCKED}, {@link IdentityErrorCode#ACCOUNT_DISABLED} or
     *         {@link IdentityErrorCode#TENANT_SUSPENDED}
     */
    public SignedInAccount authenticate(@Nullable String username, @Nullable String password)
    {
        String name = UserAccount.normalize(username);
        UserAccount found = name.isEmpty()
                ? null
                : TenantContext.callAsSystem(() -> accounts.findByUsernameNorm(name)).orElse(null);
        Long tenantId = found == null ? null : found.getTenantId();
        if (found == null || tenantId == null) {
            encoder.matches(password == null ? "" : password, unknownAccountHash);
            audit.record(new AuditRecord(AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, null, null, name, null,
                    IdentityErrorCode.INVALID_CREDENTIALS.code()));
            throw failure(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        long accountId = found.requireId();
        Instant now = clock.instant();
        Attempt attempt = TenantContext.callInTenant(tenantId,
                () -> requireNonNull(transactions.execute(status -> attempt(accountId, tenantId, password, now))));
        SignedInAccount account = attempt.account();
        // Audited after the attempt committed, so the failure counter and the trail agree.
        if (attempt.lockedNow()) {
            audit.record(new AuditRecord(AuditAction.ACCOUNT_LOCKED, AuditOutcome.SUCCESS, tenantId, accountId, name,
                    null, null));
        }
        if (account == null) {
            IdentityErrorCode error = requireNonNull(attempt.error());
            audit.record(new AuditRecord(AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, tenantId, accountId, name, null,
                    error.code()));
            throw failure(error);
        }
        audit.record(new AuditRecord(AuditAction.LOGIN_SUCCEEDED, AuditOutcome.SUCCESS, tenantId, accountId, name, null,
                null));
        return account;
    }

    private Attempt attempt(long accountId, long tenantId, @Nullable String password, Instant now)
    {
        UserAccount account = accounts.findById(accountId).orElse(null);
        if (account == null) {
            return Attempt.failed(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        if (account.isLocked(now)) {
            return Attempt.failed(IdentityErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwords.verify(account, password)) {
            boolean locked = account.recordFailedLogin(now, lockout.maxAttempts(), lockout.duration());
            return new Attempt(null, locked ? IdentityErrorCode.ACCOUNT_LOCKED : IdentityErrorCode.INVALID_CREDENTIALS,
                    locked);
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return Attempt.failed(IdentityErrorCode.ACCOUNT_DISABLED);
        }
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) {
            return Attempt.failed(IdentityErrorCode.TENANT_SUSPENDED);
        }
        account.recordSuccessfulLogin(now);
        return new Attempt(new SignedInAccount(accountId, tenantId, account.getUsername(), account.getDisplayName(),
                account.isMustChangePassword() || passwords.isExpired(account, now)), null, false);
    }

    private static GrantForgeException failure(IdentityErrorCode code)
    {
        return new GrantForgeException(code, "sign-in rejected: " + code.name());
    }

    /** Outcome of one attempt: an account on success, otherwise the reason; and whether it locked the account. */
    private record Attempt(@Nullable SignedInAccount account, @Nullable IdentityErrorCode error, boolean lockedNow)
    {
        static Attempt failed(IdentityErrorCode error)
        {
            return new Attempt(null, error, false);
        }
    }
}
