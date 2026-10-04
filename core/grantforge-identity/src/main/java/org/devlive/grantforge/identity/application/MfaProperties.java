// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Two-step sign-in settings ({@code grantforge.security.mfa.*}, D-71).
 *
 * @param stepUpWindow how long a second factor, given at sign-in or later, covers sensitive operations such as
 *         rotating signing keys or resetting passwords; 1 minute to 12 hours
 * @param requiredForSensitive whether sensitive operations need an account with two-step sign-in; off by default,
 *         so accounts without it are not asked
 */
@ConfigurationProperties("grantforge.security.mfa")
public record MfaProperties(@DefaultValue("10m") Duration stepUpWindow, @DefaultValue("false") boolean requiredForSensitive)
{
    /**
     * Validates the settings.
     *
     * @param stepUpWindow the window
     * @param requiredForSensitive whether sensitive operations need two-step sign-in
     * @throws IllegalArgumentException if the window is out of range
     */
    public MfaProperties
    {
        requireNonNull(stepUpWindow, "stepUpWindow");
        if (stepUpWindow.compareTo(Duration.ofMinutes(1)) < 0 || stepUpWindow.compareTo(Duration.ofHours(12)) > 0) {
            throw new IllegalArgumentException("grantforge.security.mfa.step-up-window must be 1m-12h but was " + stepUpWindow);
        }
    }
}
