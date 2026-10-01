// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Account security settings ({@code grantforge.security.*}).
 *
 * @param registrationEnabled whether visitors may create their own account (D-22); off by default
 * @param password the password policy
 * @param lockout the sign-in lockout rule
 */
@ConfigurationProperties("grantforge.security")
public record SecurityProperties(
        @DefaultValue("false") boolean registrationEnabled,
        @DefaultValue Password password,
        @DefaultValue Lockout lockout)
{
    /**
     * Validates the settings.
     *
     * @param registrationEnabled whether self-registration is allowed
     * @param password the password policy
     * @param lockout the lockout rule
     */
    public SecurityProperties
    {
        requireNonNull(password, "password");
        requireNonNull(lockout, "lockout");
    }

    /**
     * Temporary lockout after repeated failed sign-ins ({@code grantforge.security.lockout.*}).
     *
     * @param maxAttempts consecutive failures that lock the account, 1-100
     * @param duration how long the account stays locked; positive
     */
    public record Lockout(@DefaultValue("5") int maxAttempts, @DefaultValue("15m") Duration duration)
    {
        /**
         * Validates the rule.
         *
         * @param maxAttempts failures before locking
         * @param duration lock duration
         * @throws IllegalArgumentException if a value is out of range
         */
        public Lockout
        {
            if (maxAttempts < 1 || maxAttempts > 100) {
                throw new IllegalArgumentException("grantforge.security.lockout.max-attempts must be 1-100 but was "
                        + maxAttempts);
            }
            requireNonNull(duration, "duration");
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("grantforge.security.lockout.duration must be positive");
            }
        }

        /**
         * Returns the default rule: five failures lock the account for fifteen minutes.
         *
         * @return the defaults
         */
        public static Lockout defaults()
        {
            return new Lockout(5, Duration.ofMinutes(15));
        }
    }

    /**
     * Password policy ({@code grantforge.security.password.*}).
     *
     * @param minLength minimum number of characters; at least 8
     * @param maxLength maximum number of characters; at least {@code minLength} and at most 1024
     * @param requiredCharacterClasses how many of lowercase letters, uppercase letters, digits and other
     *         characters a password must mix, 1 (no requirement) to 4
     * @param historySize how many previous passwords a new one must differ from, 0 (no check) to 24
     * @param maxAge how long a password stays valid before the user must change it; {@code null} means forever
     * @param legacyCharset character set the pre-rebuild server used to hash passwords ({@code {sha256-legacy}}
     *         hashes imported by the migration tool); only matters for non-ASCII passwords
     */
    public record Password(
            @DefaultValue("12") int minLength,
            @DefaultValue("128") int maxLength,
            @DefaultValue("1") int requiredCharacterClasses,
            @DefaultValue("0") int historySize,
            @Nullable Duration maxAge,
            @DefaultValue("UTF-8") Charset legacyCharset)
    {
        /** Largest supported {@code historySize}. */
        public static final int MAX_HISTORY = 24;

        /**
         * Validates the policy.
         *
         * @param minLength minimum length
         * @param maxLength maximum length
         * @param requiredCharacterClasses required character classes
         * @param historySize number of remembered passwords
         * @param maxAge maximum password age, or {@code null}
         * @param legacyCharset legacy hash character set
         * @throws IllegalArgumentException if a value is out of range
         */
        public Password
        {
            if (minLength < 8 || maxLength < minLength || maxLength > 1024) {
                throw new IllegalArgumentException("grantforge.security.password requires 8 <= min-length <= max-length"
                        + " <= 1024 but was " + minLength + ".." + maxLength);
            }
            if (requiredCharacterClasses < 1 || requiredCharacterClasses > 4) {
                throw new IllegalArgumentException("grantforge.security.password.required-character-classes must be"
                        + " 1-4 but was " + requiredCharacterClasses);
            }
            if (historySize < 0 || historySize > MAX_HISTORY) {
                throw new IllegalArgumentException("grantforge.security.password.history-size must be 0-" + MAX_HISTORY
                        + " but was " + historySize);
            }
            if (maxAge != null && (maxAge.isNegative() || maxAge.isZero())) {
                throw new IllegalArgumentException("grantforge.security.password.max-age must be positive");
            }
            requireNonNull(legacyCharset, "legacyCharset");
        }

        /**
         * Returns the default policy: 12-128 characters, no composition rule, no history, no expiry.
         *
         * @return the defaults
         */
        public static Password defaults()
        {
            return new Password(12, 128, 1, 0, null, StandardCharsets.UTF_8);
        }
    }
}
