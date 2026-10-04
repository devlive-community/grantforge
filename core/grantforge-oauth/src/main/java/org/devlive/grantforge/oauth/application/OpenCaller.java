// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.jspecify.annotations.Nullable;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Who calls the open API: an application of the catalog, through one of its OAuth clients, for an account or for itself.
 *
 * @param clientId the client's public identifier
 * @param applicationId the client's application, whose permissions the caller may ask about
 * @param subject the account the token was issued for, or {@code null} for a token the client obtained for itself
 * @param scopes the scopes of the token
 */
public record OpenCaller(String clientId, long applicationId, @Nullable OAuthSubject subject, Set<String> scopes)
{
    /** Scope a token needs to read permissions. */
    public static final String PERMISSIONS = "permissions";

    /** Checks and copies the parts. */
    public OpenCaller
    {
        requireNonNull(clientId, "clientId");
        scopes = Set.copyOf(scopes);
    }

    /**
     * Returns whether the token may read permissions.
     *
     * @return {@code true} if it carries the {@value #PERMISSIONS} scope
     */
    public boolean mayReadPermissions()
    {
        return scopes.contains(PERMISSIONS);
    }
}
