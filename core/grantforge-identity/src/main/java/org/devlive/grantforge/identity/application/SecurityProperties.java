// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static java.util.Objects.requireNonNull;

/**
 * Account security settings ({@code grantforge.security.*}).
 *
 * @param registrationEnabled whether visitors may create their own account (D-22); off by default
 * @param password the password policy
 */
@ConfigurationProperties("grantforge.security")
public record SecurityProperties(@DefaultValue("false") boolean registrationEnabled, @DefaultValue Password password)
{
    /**
     * Validates the settings.
     *
     * @param registrationEnabled whether self-registration is allowed
     * @param password the password policy
     */
    public SecurityProperties
    {
        requireNonNull(password, "password");
    }

    /**
     * Password policy ({@code grantforge.security.password.*}).
     *
     * @param minLength minimum number of characters; at least 8
     * @param maxLength maximum number of characters; at least {@code minLength} and at most 1024
     */
    public record Password(@DefaultValue("12") int minLength, @DefaultValue("128") int maxLength)
    {
        /**
         * Validates the policy.
         *
         * @param minLength minimum length
         * @param maxLength maximum length
         * @throws IllegalArgumentException if the lengths are out of range
         */
        public Password
        {
            if (minLength < 8 || maxLength < minLength || maxLength > 1024) {
                throw new IllegalArgumentException("grantforge.security.password requires 8 <= min-length <= max-length"
                        + " <= 1024 but was " + minLength + ".." + maxLength);
            }
        }
    }
}
