// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/** Reads the signed-in user's profile. Must be called with the user's tenant bound. */
@Service
public final class ProfileService
{
    private final UserAccountRepository accounts;
    private final TenantRepository tenants;
    private final PasswordService passwords;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param accounts user accounts
     * @param tenants tenants
     * @param passwords decides whether the password expired
     * @param transactionManager opens the read-only transaction
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
