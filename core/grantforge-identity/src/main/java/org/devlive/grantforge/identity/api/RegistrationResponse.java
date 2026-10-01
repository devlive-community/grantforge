// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import static java.util.Objects.requireNonNull;

/**
 * The account a visitor registered.
 *
 * @param username the login name to sign in with
 */
public record RegistrationResponse(String username)
{
    /** Validates the value. */
    public RegistrationResponse
    {
        requireNonNull(username, "username");
    }
}
