// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First-run setup settings ({@code grantforge.setup.*}).
 *
 * @param token a fixed setup token for automated installations (for example {@code GRANTFORGE_SETUP_TOKEN});
 *         when absent a random token is generated and logged at start-up. Blank means absent.
 */
@ConfigurationProperties("grantforge.setup")
public record SetupProperties(@Nullable String token)
{
    /** Shortest accepted fixed token. */
    public static final int MIN_TOKEN_LENGTH = 16;

    /**
     * Normalizes and validates the settings.
     *
     * @param token the fixed token, or {@code null}
     * @throws IllegalArgumentException if a fixed token is shorter than {@value #MIN_TOKEN_LENGTH} characters
     */
    public SetupProperties
    {
        token = Strings.blankToNull(token);
        if (token != null && token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalArgumentException("grantforge.setup.token must have at least " + MIN_TOKEN_LENGTH
                    + " characters");
        }
    }
}
