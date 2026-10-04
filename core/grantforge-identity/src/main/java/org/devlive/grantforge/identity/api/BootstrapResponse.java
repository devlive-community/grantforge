// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import org.devlive.grantforge.identity.application.SignInOption;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What the console needs to know before anyone signs in.
 *
 * @param setupRequired whether first-run setup must happen before anything else
 * @param registrationEnabled whether visitors may create their own account
 * @param signInSources the identity providers users may sign in with instead of a password
 */
public record BootstrapResponse(boolean setupRequired, boolean registrationEnabled, List<SignInOption> signInSources)
{
    /** Copies the providers. */
    public BootstrapResponse
    {
        signInSources = List.copyOf(requireNonNull(signInSources, "signInSources"));
    }
}
