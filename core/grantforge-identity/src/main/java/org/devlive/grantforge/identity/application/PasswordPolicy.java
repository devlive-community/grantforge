// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Checks new passwords against the configured policy before they are hashed. Length counts Unicode code
 * points, so a password of emoji is measured the way the user sees it.
 */
@Component
public final class PasswordPolicy
{
    /** Login names shorter than this are not searched for inside passwords. */
    private static final int MIN_USERNAME_CHECK = 3;

    private final SecurityProperties.Password settings;

    /**
     * Creates the policy.
     *
     * @param properties the security settings
     */
    public PasswordPolicy(SecurityProperties properties)
    {
        this.settings = requireNonNull(properties, "properties").password();
    }

    /**
     * Validates a password for an account.
     *
     * @param password the raw password; {@code null} counts as empty
     * @param username the account's login name, or {@code null} if not known yet
     * @return the password, now known to be non-null and valid
     * @throws GrantForgeException with an {@link IdentityErrorCode} for the first rule the password breaks
     */
    public String check(@Nullable String password, @Nullable String username)
    {
        String value = password == null ? "" : password;
        int length = value.codePointCount(0, value.length());
        if (length < settings.minLength()) {
            throw new GrantForgeException(IdentityErrorCode.PASSWORD_TOO_SHORT,
                    "password has " + length + " characters", settings.minLength());
        }
        if (length > settings.maxLength()) {
            throw new GrantForgeException(IdentityErrorCode.PASSWORD_TOO_LONG,
                    "password has " + length + " characters", settings.maxLength());
        }
        int classes = characterClasses(value);
        if (classes < settings.requiredCharacterClasses()) {
            throw new GrantForgeException(IdentityErrorCode.PASSWORD_TOO_SIMPLE,
                    "password mixes " + classes + " character classes", settings.requiredCharacterClasses());
        }
        String name = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (name.length() >= MIN_USERNAME_CHECK && value.toLowerCase(Locale.ROOT).contains(name)) {
            throw new GrantForgeException(IdentityErrorCode.PASSWORD_CONTAINS_USERNAME, "password contains the username");
        }
        return value;
    }

    /** Counts which of lowercase, uppercase, digit and other characters occur. */
    static int characterClasses(String value)
    {
        return Integer.bitCount(value.codePoints().map(PasswordPolicy::characterClass).reduce(0, (seen, bit) -> seen | bit));
    }

    private static int characterClass(int codePoint)
    {
        if (Character.isLowerCase(codePoint)) {
            return 1;
        }
        if (Character.isUpperCase(codePoint)) {
            return 2;
        }
        return Character.isDigit(codePoint) ? 4 : 8;
    }
}
