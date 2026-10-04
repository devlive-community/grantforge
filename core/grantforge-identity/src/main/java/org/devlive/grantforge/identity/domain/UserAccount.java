// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.secured.FilterableField;
import org.devlive.grantforge.persistence.secured.SecuredEntity;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A person or service that can sign in. Belongs to one tenant; the login name is unique across all tenants
 * (compared case-insensitively through {@code usernameNorm}), so signing in never needs a tenant.
 */
@Entity
@Table(name = "gf_user_account")
@SecuredEntity(code = "user", name = "Users", owner = "id", unitFromOwner = true)
public class UserAccount
        extends TenantScopedEntity
{
    /** Allowed login names: 3-64 letters, digits, dots, underscores, at signs or hyphens. */
    public static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._@-]{3,64}");

    /** Accepted e-mail addresses: one {@code @} with text around it and no whitespace (deliverability is not checked). */
    public static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+");

    /** Longest display name. */
    public static final int MAX_DISPLAY_NAME = 128;

    /** Longest e-mail address (RFC 5321 path limit). */
    public static final int MAX_EMAIL = 254;

    /**
     * End of an administrator's lock, which lasts until someone unlocks the account. Early in year 9999 so that
     * every database (and any time zone conversion) can store it.
     */
    public static final Instant LOCKED_INDEFINITELY = Instant.parse("9999-01-01T00:00:00Z");

    @FilterableField("User name")
    @Column(name = "username", nullable = false, length = 64)
    private String username = "";

    @Column(name = "username_norm", nullable = false, length = 64)
    private String usernameNorm = "";

    @FilterableField("Display name")
    @Column(name = "display_name", length = 128)
    private @Nullable String displayName;

    @FilterableField("E-mail")
    @Column(name = "email", length = 254)
    private @Nullable String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash = "";

    // Plain VARCHAR on every database (Hibernate would otherwise use a native ENUM on MySQL).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @FilterableField("Status")
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private @Nullable Instant lockedUntil;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt = Instant.EPOCH;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @FilterableField("Last sign-in")
    @Column(name = "last_login_at")
    private @Nullable Instant lastLoginAt;

    @Column(name = "system_account", nullable = false)
    private boolean systemAccount;

    /** For JPA. */
    protected UserAccount()
    {
    }

    /**
     * Creates an active account.
     *
     * @param username the login name; see {@link #USERNAME}
     * @param passwordHash an encoded password (never a raw password)
     * @param now the current time
     * @return the new account
     * @throws IllegalArgumentException if the username is invalid or the hash is blank
     */
    public static UserAccount create(String username, String passwordHash, Instant now)
    {
        UserAccount account = new UserAccount();
        account.username = validUsername(username);
        account.usernameNorm = normalize(account.username);
        account.passwordHash = Strings.requireNonBlank(passwordHash, "passwordHash");
        account.passwordChangedAt = requireNonNull(now, "now");
        return account;
    }

    /**
     * Returns the canonical form used for uniqueness and lookup.
     *
     * @param username a login name; may be {@code null}
     * @return the lowercase, trimmed name, or an empty string for {@code null}
     */
    public static String normalize(@Nullable String username)
    {
        String trimmed = Strings.blankToNull(username);
        return trimmed == null ? "" : trimmed.toLowerCase(Locale.ROOT);
    }

    private static String validUsername(String username)
    {
        String trimmed = Strings.requireNonBlank(username, "username");
        if (!USERNAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("username must be 3-64 letters, digits, '.', '_', '@' or '-'");
        }
        return trimmed;
    }

    /**
     * Marks the account as a protected system account (for example the first administrator).
     *
     * @return this account
     */
    public UserAccount markSystemAccount()
    {
        systemAccount = true;
        return this;
    }

    /**
     * Sets the optional display name; blank means none.
     *
     * @param value the name; may be {@code null}
     * @return this account
     * @throws IllegalArgumentException if the name is longer than {@link #MAX_DISPLAY_NAME}
     */
    public UserAccount withDisplayName(@Nullable String value)
    {
        String name = Strings.blankToNull(value);
        if (name != null && name.length() > MAX_DISPLAY_NAME) {
            throw new IllegalArgumentException("display name longer than " + MAX_DISPLAY_NAME + " characters");
        }
        displayName = name;
        return this;
    }

    /**
     * Sets the optional e-mail address; blank means none.
     *
     * @param value the address; may be {@code null}
     * @return this account
     * @throws IllegalArgumentException if the address is malformed or longer than {@link #MAX_EMAIL}
     */
    public UserAccount withEmail(@Nullable String value)
    {
        String address = Strings.blankToNull(value);
        if (address != null && (address.length() > MAX_EMAIL || !EMAIL.matcher(address).matches())) {
            throw new IllegalArgumentException("malformed e-mail address");
        }
        email = address;
        return this;
    }

    /**
     * Replaces the password and clears lockout state.
     *
     * @param newHash the encoded password
     * @param now the current time
     */
    public void changePassword(String newHash, Instant now)
    {
        passwordHash = Strings.requireNonBlank(newHash, "newHash");
        passwordChangedAt = requireNonNull(now, "now");
        mustChangePassword = false;
        clearLockout();
    }

    /** Blocks sign-in until {@link #enable()}; existing sessions must be revoked by the caller. */
    public void disable()
    {
        status = AccountStatus.DISABLED;
    }

    /** Allows sign-in again. */
    public void enable()
    {
        status = AccountStatus.ACTIVE;
    }

    /** Locks the account until an administrator unlocks it; existing sessions must be revoked by the caller. */
    public void lockIndefinitely()
    {
        failedAttempts = 0;
        lockedUntil = LOCKED_INDEFINITELY;
    }

    /** Lifts any lock, an administrator's or one after failed sign-ins, and forgets the failed attempts. */
    public void unlock()
    {
        clearLockout();
    }

    /** Forces the user to choose a new password at the next sign-in (for example after an administrator reset). */
    public void requirePasswordChange()
    {
        mustChangePassword = true;
    }

    /**
     * Replaces the stored hash of the same password with a stronger encoding; unlike
     * {@link #changePassword} it is not a password change and keeps the password age.
     *
     * @param newHash the re-encoded password
     */
    public void rehashPassword(String newHash)
    {
        passwordHash = Strings.requireNonBlank(newHash, "newHash");
    }

    /**
     * Records a failed sign-in and locks the account when the limit is reached.
     *
     * @param now the current time
     * @param maxAttempts failures allowed before locking; at least 1
     * @param lockDuration how long the account stays locked
     * @return {@code true} if this failure locked the account
     * @throws IllegalArgumentException if {@code maxAttempts} is below 1
     */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockDuration)
    {
        requireNonNull(now, "now");
        requireNonNull(lockDuration, "lockDuration");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedAttempts = 0;
            return true;
        }
        return false;
    }

    /**
     * Records a successful sign-in.
     *
     * @param now the current time
     */
    public void recordSuccessfulLogin(Instant now)
    {
        lastLoginAt = requireNonNull(now, "now");
        clearLockout();
    }

    // NULL is how the nullable locked_until column says "not locked".
    @SuppressWarnings("PMD.NullAssignment")
    private void clearLockout()
    {
        failedAttempts = 0;
        lockedUntil = null;
    }

    /**
     * Returns whether a lockout is in effect.
     *
     * @param now the current time
     * @return {@code true} while {@code now} is before the lockout end
     */
    public boolean isLocked(Instant now)
    {
        Instant until = lockedUntil;
        return until != null && now.isBefore(until);
    }

    /**
     * Returns when the current lock ends.
     *
     * @return the end of the lock, {@link #LOCKED_INDEFINITELY} for an administrator's lock, or {@code null}
     */
    public @Nullable Instant getLockedUntil()
    {
        return lockedUntil;
    }

    /**
     * Returns the login name as entered at creation.
     *
     * @return the username
     */
    public String getUsername()
    {
        return username;
    }

    /**
     * Returns the canonical login name.
     *
     * @return the lowercase username
     */
    public String getUsernameNorm()
    {
        return usernameNorm;
    }

    /**
     * Returns the display name.
     *
     * @return the display name, or {@code null}
     */
    public @Nullable String getDisplayName()
    {
        return displayName;
    }

    /**
     * Returns the e-mail address.
     *
     * @return the address, or {@code null}
     */
    public @Nullable String getEmail()
    {
        return email;
    }

    /**
     * Returns the encoded password.
     *
     * @return the hash with its algorithm prefix
     */
    public String getPasswordHash()
    {
        return passwordHash;
    }

    /**
     * Returns the administrative state.
     *
     * @return the status
     */
    public AccountStatus getStatus()
    {
        return status;
    }

    /**
     * Returns consecutive failed sign-ins since the last success or lockout.
     *
     * @return the count
     */
    public int getFailedAttempts()
    {
        return failedAttempts;
    }

    /**
     * Returns when the password was last set.
     *
     * @return the time
     */
    public Instant getPasswordChangedAt()
    {
        return passwordChangedAt;
    }

    /**
     * Returns whether the user must change the password at the next sign-in.
     *
     * @return the flag
     */
    public boolean isMustChangePassword()
    {
        return mustChangePassword;
    }

    /**
     * Returns the time of the last successful sign-in.
     *
     * @return the time, or {@code null} if the account never signed in
     */
    public @Nullable Instant getLastLoginAt()
    {
        return lastLoginAt;
    }

    /**
     * Returns whether this is a protected system account.
     *
     * @return the flag
     */
    public boolean isSystemAccount()
    {
        return systemAccount;
    }
}
