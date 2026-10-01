// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.PasswordHistory;
import org.devlive.grantforge.identity.domain.PasswordHistoryRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Everything that touches passwords: hashing new ones under the policy, verifying (and silently upgrading
 * old encodings), changing them with reuse protection, and expiry.
 *
 * <p>Methods that modify an account expect to run inside a transaction bound to the account's tenant;
 * the caller saves nothing explicitly because the account is a managed entity.
 */
@Service
public final class PasswordService
{
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final PasswordHistoryRepository history;
    private final SecurityProperties.Password settings;

    /**
     * Creates the service.
     *
     * @param encoder hashes and verifies passwords
     * @param policy validates new passwords
     * @param history former passwords
     * @param properties security settings
     */
    public PasswordService(PasswordEncoder encoder, PasswordPolicy policy, PasswordHistoryRepository history,
            SecurityProperties properties)
    {
        this.encoder = requireNonNull(encoder, "encoder");
        this.policy = requireNonNull(policy, "policy");
        this.history = requireNonNull(history, "history");
        this.settings = requireNonNull(properties, "properties").password();
    }

    /**
     * Checks a new password against the policy and hashes it.
     *
     * @param password the raw password
     * @param username the account's login name, so the policy can reject passwords containing it
     * @return the encoded password
     * @throws GrantForgeException if the password breaks the policy
     */
    public String hashNew(@Nullable String password, @Nullable String username)
    {
        return encoder.encode(policy.check(password, username));
    }

    /**
     * Verifies a password. When it matches and the stored hash uses an outdated encoding (BCrypt, or the
     * imported legacy SHA-256), the account gets a fresh hash of the same password.
     *
     * @param account the account
     * @param password the raw password; {@code null} or empty never matches
     * @return whether the password is correct
     */
    public boolean verify(UserAccount account, @Nullable String password)
    {
        requireNonNull(account, "account");
        if (password == null || password.isEmpty()) {
            return false;
        }
        String stored = account.getPasswordHash();
        if (!encoder.matches(password, stored)) {
            return false;
        }
        if (encoder.upgradeEncoding(stored)) {
            account.rehashPassword(encoder.encode(password));
        }
        return true;
    }

    /**
     * Changes the password after confirming the current one.
     *
     * @param account the account
     * @param current the current password
     * @param next the new password
     * @param now the current time
     * @throws GrantForgeException {@link IdentityErrorCode#PASSWORD_INCORRECT} for a wrong current password,
     *         or a policy error for the new one
     */
    public void change(UserAccount account, @Nullable String current, @Nullable String next, Instant now)
    {
        if (!verify(account, current)) {
            throw new GrantForgeException(IdentityErrorCode.PASSWORD_INCORRECT, "current password does not match");
        }
        replace(account, next, now);
    }

    /**
     * Sets a new password under the policy and the reuse rule, remembering the old one. The caller must have
     * authorized the change (the user confirmed the current password, or an administrator resets it).
     *
     * @param account a persisted account
     * @param next the new password
     * @param now the current time
     * @throws GrantForgeException if the password breaks the policy or was used recently
     */
    public void replace(UserAccount account, @Nullable String next, Instant now)
    {
        requireNonNull(account, "account");
        requireNonNull(now, "now");
        String hash = hashNew(next, account.getUsername());
        int remembered = settings.historySize();
        if (remembered > 0) {
            long accountId = account.requireId();
            // The current password counts as the most recent one, so remembered - 1 entries are kept.
            List<String> recent = new ArrayList<>();
            recent.add(account.getPasswordHash());
            history.findByAccountIdOrderByCreatedAtDescIdDesc(accountId, Limit.of(remembered))
                    .forEach(entry -> recent.add(entry.getPasswordHash()));
            for (String former : recent.subList(0, Math.min(remembered, recent.size()))) {
                if (encoder.matches(next, former)) {
                    throw new GrantForgeException(IdentityErrorCode.PASSWORD_REUSED, "password was used recently",
                            remembered);
                }
            }
            if (remembered > 1) {
                history.save(PasswordHistory.of(accountId, account.getPasswordHash()));
                List<PasswordHistory> all = history.findByAccountIdOrderByCreatedAtDescIdDesc(accountId);
                if (all.size() > remembered - 1) {
                    history.deleteAllInBatch(all.subList(remembered - 1, all.size()));
                }
            }
        }
        account.changePassword(hash, now);
    }

    /**
     * Returns whether the password is older than the policy allows.
     *
     * @param account the account
     * @param now the current time
     * @return {@code true} if a maximum age is configured and reached
     */
    public boolean isExpired(UserAccount account, Instant now)
    {
        Duration maxAge = settings.maxAge();
        return maxAge != null && !now.isBefore(account.getPasswordChangedAt().plus(maxAge));
    }
}
