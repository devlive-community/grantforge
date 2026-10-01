// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/** Reads and changes the signed-in user's profile and password. Must be called with the user's tenant bound. */
@Service
public final class ProfileService
{
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final PasswordService passwords;
    private final TransactionTemplate transactions;
    private final TransactionTemplate writes;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param accounts user accounts
     * @param tenants tenants
     * @param passwords decides whether the password expired
     * @param transactionManager opens transactions
     * @param clock source of the current time
     */
    public ProfileService(UserAccountRepository accounts, TenantRepository tenants, PasswordService passwords,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.tenants = requireNonNull(tenants, "tenants");
        this.passwords = requireNonNull(passwords, "passwords");
        TransactionTemplate template = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        template.setReadOnly(true);
        this.transactions = template;
        this.writes = new TransactionTemplate(transactionManager);
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns an account's profile.
     *
     * @param accountId the account
     * @return the profile, or empty if the account no longer exists in the bound tenant
     */
    public Optional<AccountProfile> find(long accountId)
    {
        return requireNonNull(transactions.execute(status -> accounts.findById(accountId).flatMap(this::profile)));
    }

    /**
     * Changes the user's own display name and e-mail address; blank values clear them.
     *
     * @param accountId the account
     * @param displayName the new display name, at most {@link UserAccount#MAX_DISPLAY_NAME} characters
     * @param email the new e-mail address
     * @return the updated profile
     * @throws GrantForgeException with {@link CommonErrorCode#BAD_REQUEST} for a malformed value, or
     *         {@link CommonErrorCode#UNAUTHENTICATED} if the account no longer exists
     */
    public AccountProfile update(long accountId, @Nullable String displayName, @Nullable String email)
    {
        return requireNonNull(writes.execute(status -> {
            UserAccount account = require(accountId);
            try {
                account.withDisplayName(displayName).withEmail(email);
            }
            catch (IllegalArgumentException invalid) {
                throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
            }
            return profile(account).orElseThrow(() -> gone(accountId));
        }));
    }

    /**
     * Changes the user's own password after confirming the current one; clears a pending forced change.
     *
     * @param accountId the account
     * @param current the current password
     * @param next the new password
     * @throws GrantForgeException {@link IdentityErrorCode#PASSWORD_INCORRECT} for a wrong current password, a
     *         policy error for the new one, or {@link CommonErrorCode#UNAUTHENTICATED} if the account is gone
     */
    public void changePassword(long accountId, @Nullable String current, @Nullable String next)
    {
        writes.executeWithoutResult(status -> passwords.change(require(accountId), current, next, clock.instant()));
    }

    private UserAccount require(long accountId)
    {
        return accounts.findById(accountId).orElseThrow(() -> gone(accountId));
    }

    private static GrantForgeException gone(long accountId)
    {
        return new GrantForgeException(CommonErrorCode.UNAUTHENTICATED, "account " + accountId + " no longer exists");
    }

    private Optional<AccountProfile> profile(UserAccount account)
    {
        Long tenantId = account.getTenantId();
        Optional<Tenant> tenant = tenantId == null ? Optional.empty() : tenants.findById(tenantId);
        return tenant.map(owner -> new AccountProfile(account.requireId(), account.getUsername(),
                account.getDisplayName(), account.getEmail(), owner.getCode(), owner.getName(),
                account.isSystemAccount(),
                account.isMustChangePassword() || passwords.isExpired(account, clock.instant()),
                account.getLastLoginAt()));
    }
}
