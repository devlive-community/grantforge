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
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
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
 *
 * <p>An account with two-step sign-in (D-71) is not signed in by its password alone: the right password yields an
 * account that still needs {@link #completeSecondFactor}, and a wrong code counts towards the lockout as a wrong
 * password does.
 *
 * <p>Accounts of an identity source (D-72) are checked by the source instead of the password kept here; a name no
 * account has is offered to the directories that create accounts before it is refused.
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
    private final MfaService mfa;
    private final ExternalAccounts externals;
    private final IdentitySourceRepository sources;

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
     * @param mfa checks second factors
     * @param externals checks the passwords of accounts of identity sources and signs up their new users
     * @param sources finds the providers users sign in with
     */
    public AuthenticationService(UserAccountRepository accounts, TenantRepository tenants, PasswordService passwords,
            PasswordEncoder encoder, SecurityProperties properties, PlatformTransactionManager transactionManager,
            Clock clock, AuditLog audit, MfaService mfa, ExternalAccounts externals, IdentitySourceRepository sources)
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
        this.mfa = requireNonNull(mfa, "mfa");
        this.externals = requireNonNull(externals, "externals");
        this.sources = requireNonNull(sources, "sources");
    }

    /**
     * Signs an account in.
     *
     * @param username the login name in any case; {@code null} never matches
     * @param password the password; {@code null} never matches
     * @return the signed-in account, or one that still needs its second factor
     * @throws GrantForgeException {@link IdentityErrorCode#INVALID_CREDENTIALS},
     *         {@link IdentityErrorCode#ACCOUNT_LOCKED}, {@link IdentityErrorCode#ACCOUNT_LOCKED_BY_ADMINISTRATOR},
     *         {@link IdentityErrorCode#ACCOUNT_DISABLED} or
     *         {@link IdentityErrorCode#TENANT_SUSPENDED}
     */
    public SignedInAccount authenticate(@Nullable String username, @Nullable String password)
    {
        String name = UserAccount.normalize(username);
        UserAccount found = name.isEmpty()
                ? null
                : TenantContext.callAsSystem(() -> accounts.findByUsernameNorm(name)).orElse(null);
        if (found == null && !name.isEmpty()) {
            found = externals.signUp(String.valueOf(username).trim(), password)
                    .flatMap(created -> TenantContext.callAsSystem(() -> accounts.findById(created))).orElse(null);
        }
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
        return conclude(attempt, tenantId, accountId, name);
    }

    /**
     * Completes a sign-in whose password was right with the account's second factor: a code of its authenticator or a
     * recovery code. The account is checked again, as it may have been disabled or locked in between.
     *
     * @param accountId the account that gave the right password
     * @param tenantId its tenant
     * @param code the code entered; {@code null} never matches
     * @return the signed-in account
     * @throws GrantForgeException {@link IdentityErrorCode#MFA_CODE_INVALID}, {@link IdentityErrorCode#ACCOUNT_LOCKED},
     *         {@link IdentityErrorCode#ACCOUNT_LOCKED_BY_ADMINISTRATOR}, {@link IdentityErrorCode#ACCOUNT_DISABLED},
     *         {@link IdentityErrorCode#TENANT_SUSPENDED} or {@link IdentityErrorCode#INVALID_CREDENTIALS} for an
     *         account gone in between
     */
    public SignedInAccount completeSecondFactor(long accountId, long tenantId, @Nullable String code)
    {
        Instant now = clock.instant();
        Attempt attempt = TenantContext.callInTenant(tenantId,
                () -> requireNonNull(transactions.execute(status -> secondStep(accountId, tenantId, code, now, true))));
        String name = TenantContext.callInTenant(tenantId,
                () -> accounts.findById(accountId).map(found -> UserAccount.normalize(found.getUsername())).orElse(null));
        return conclude(attempt, tenantId, accountId, name);
    }

    /**
     * Signs in a user an OpenID Connect provider vouched for, with an ID token the caller verified. The user's account is
     * found by what the provider calls them, or created if the source creates accounts; it is then checked as at a
     * password sign-in, and an account with two-step sign-in still needs its second factor.
     *
     * @param sourceCode the code of the source the user signed in with
     * @param user the user as the ID token describes them
     * @return the signed-in account, or one that still needs its second factor
     * @throws GrantForgeException {@link IdentityErrorCode#FEDERATED_SIGN_IN_FAILED} for an unknown or disabled source,
     *         {@link IdentityErrorCode#FEDERATED_ACCOUNT_UNKNOWN}, {@link IdentityErrorCode#EXTERNAL_ACCOUNT_CONFLICT},
     *         {@link IdentityErrorCode#ACCOUNT_LOCKED}, {@link IdentityErrorCode#ACCOUNT_LOCKED_BY_ADMINISTRATOR},
     *         {@link IdentityErrorCode#ACCOUNT_DISABLED} or {@link IdentityErrorCode#TENANT_SUSPENDED}
     */
    public SignedInAccount signInFederated(String sourceCode, DirectoryUser user)
    {
        IdentitySource source = TenantContext.callAsSystem(() -> sources.findByCode(sourceCode))
                .filter(found -> found.isEnabled() && found.getType() == IdentitySourceType.OIDC)
                .orElseThrow(() -> new GrantForgeException(IdentityErrorCode.FEDERATED_SIGN_IN_FAILED, "no provider " + sourceCode,
                        "unknown or disabled identity source"));
        long tenantId = requireNonNull(source.getTenantId(), "tenantId");
        long sourceId = source.requireId();
        String name = UserAccount.normalize(user.username());
        Instant now = clock.instant();
        Federated federated;
        try {
            federated = TenantContext.callInTenant(tenantId, () -> requireNonNull(transactions.execute(status -> {
                IdentitySource current = sources.findById(sourceId).orElseThrow(() -> new IllegalStateException("source vanished"));
                UserAccount account = externals.provision(current, user);
                return new Federated(account.requireId(), federatedAttempt(account, tenantId, now));
            })));
        }
        catch (GrantForgeException refused) {
            audit.record(new AuditRecord(AuditAction.LOGIN_FAILED, AuditOutcome.FAILURE, tenantId, null, name, null,
                    refused.getErrorCode().code()));
            throw refused;
        }
        return conclude(federated.attempt(), tenantId, federated.accountId(), name);
    }

    /**
     * Confirms a sensitive operation of a signed-in account with its second factor. A wrong code counts towards the
     * lockout as at sign-in, so a session cannot be used to guess codes.
     *
     * @param accountId the signed-in account
     * @param tenantId its tenant
     * @param code the code entered; {@code null} never matches
     * @throws GrantForgeException {@link IdentityErrorCode#MFA_CODE_INVALID} or another refusal of
     *         {@link #completeSecondFactor}
     */
    public void stepUp(long accountId, long tenantId, @Nullable String code)
    {
        Instant now = clock.instant();
        Attempt attempt = TenantContext.callInTenant(tenantId,
                () -> requireNonNull(transactions.execute(status -> secondStep(accountId, tenantId, code, now, false))));
        String name = TenantContext.callInTenant(tenantId,
                () -> accounts.findById(accountId).map(found -> UserAccount.normalize(found.getUsername())).orElse(null));
        if (attempt.lockedNow()) {
            audit.record(new AuditRecord(AuditAction.ACCOUNT_LOCKED, AuditOutcome.SUCCESS, tenantId, accountId, name,
                    null, null));
        }
        IdentityErrorCode error = attempt.error();
        audit.record(new AuditRecord(AuditAction.MFA_STEP_UP, error == null ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE, tenantId,
                accountId, name, null, error == null ? null : error.code()));
        if (error != null) {
            throw failure(error);
        }
    }

    private SignedInAccount conclude(Attempt attempt, long tenantId, long accountId, @Nullable String name)
    {
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
        // Half a sign-in is not one: the trail records it once the second factor is given.
        if (!account.secondFactorRequired()) {
            audit.record(new AuditRecord(AuditAction.LOGIN_SUCCEEDED, AuditOutcome.SUCCESS, tenantId, accountId, name,
                    null, null));
        }
        return account;
    }

    private Attempt attempt(long accountId, long tenantId, @Nullable String password, Instant now)
    {
        UserAccount account = accounts.findById(accountId).orElse(null);
        if (account == null) {
            return Attempt.failed(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        if (account.isLocked(now)) {
            // An administrator's lock does not pass by waiting, so say who to ask.
            return Attempt.failed(UserAccount.LOCKED_INDEFINITELY.equals(account.getLockedUntil())
                    ? IdentityErrorCode.ACCOUNT_LOCKED_BY_ADMINISTRATOR : IdentityErrorCode.ACCOUNT_LOCKED);
        }
        boolean right = externals.checkPassword(account, password).orElseGet(() -> passwords.verify(account, password));
        if (!right) {
            return failed(account, now, IdentityErrorCode.INVALID_CREDENTIALS);
        }
        IdentityErrorCode refused = refusal(account, tenantId);
        if (refused != null) {
            return Attempt.failed(refused);
        }
        if (mfa.enabled(accountId)) {
            return new Attempt(signedIn(account, tenantId, now, true), null, false);
        }
        account.recordSuccessfulLogin(now);
        return new Attempt(signedIn(account, tenantId, now, false), null, false);
    }

    private Attempt secondStep(long accountId, long tenantId, @Nullable String code, Instant now, boolean signIn)
    {
        UserAccount account = accounts.findById(accountId).orElse(null);
        if (account == null) {
            return Attempt.failed(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        if (account.isLocked(now)) {
            return Attempt.failed(UserAccount.LOCKED_INDEFINITELY.equals(account.getLockedUntil())
                    ? IdentityErrorCode.ACCOUNT_LOCKED_BY_ADMINISTRATOR : IdentityErrorCode.ACCOUNT_LOCKED);
        }
        IdentityErrorCode refused = refusal(account, tenantId);
        if (refused != null) {
            return Attempt.failed(refused);
        }
        if (code == null || !mfa.verify(accountId, code)) {
            return failed(account, now, IdentityErrorCode.MFA_CODE_INVALID);
        }
        if (signIn) {
            account.recordSuccessfulLogin(now);
        }
        return new Attempt(signedIn(account, tenantId, now, false), null, false);
    }

    private Attempt federatedAttempt(UserAccount account, long tenantId, Instant now)
    {
        if (account.isLocked(now)) {
            return Attempt.failed(UserAccount.LOCKED_INDEFINITELY.equals(account.getLockedUntil())
                    ? IdentityErrorCode.ACCOUNT_LOCKED_BY_ADMINISTRATOR : IdentityErrorCode.ACCOUNT_LOCKED);
        }
        IdentityErrorCode refused = refusal(account, tenantId);
        if (refused != null) {
            return Attempt.failed(refused);
        }
        if (mfa.enabled(account.requireId())) {
            return new Attempt(signedIn(account, tenantId, now, true), null, false);
        }
        account.recordSuccessfulLogin(now);
        return new Attempt(signedIn(account, tenantId, now, false), null, false);
    }

    private Attempt failed(UserAccount account, Instant now, IdentityErrorCode error)
    {
        boolean locked = account.recordFailedLogin(now, lockout.maxAttempts(), lockout.duration());
        return new Attempt(null, locked ? IdentityErrorCode.ACCOUNT_LOCKED : error, locked);
    }

    private @Nullable IdentityErrorCode refusal(UserAccount account, long tenantId)
    {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return IdentityErrorCode.ACCOUNT_DISABLED;
        }
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) {
            return IdentityErrorCode.TENANT_SUSPENDED;
        }
        return null;
    }

    private SignedInAccount signedIn(UserAccount account, long tenantId, Instant now, boolean secondFactorRequired)
    {
        // The source keeps the password of its accounts, so nothing here can demand a new one.
        boolean change = !externals.isExternal(account.requireId()) && (account.isMustChangePassword() || passwords.isExpired(account, now));
        return new SignedInAccount(account.requireId(), tenantId, account.getUsername(), account.getDisplayName(), change,
                secondFactorRequired);
    }

    private static GrantForgeException failure(IdentityErrorCode code)
    {
        return new GrantForgeException(code, "sign-in rejected: " + code.name());
    }

    /** A federated attempt with the account it concerns. */
    private record Federated(long accountId, Attempt attempt)
    {
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
