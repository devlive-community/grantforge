// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * The account an authorization is for.
 *
 * @param accountId the account
 * @param tenantId its tenant
 * @param username its login name when it signed in
 * @param authenticatedAt when it signed in, the ID token's {@code auth_time}
 */
public record OAuthSubject(long accountId, long tenantId, String username, Instant authenticatedAt)
{
    /** Checks the identifiers. */
    public OAuthSubject
    {
        if (accountId <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("account and tenant IDs must be positive");
        }
        requireNonNull(username, "username");
        requireNonNull(authenticatedAt, "authenticatedAt");
    }
}
