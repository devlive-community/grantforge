// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.jspecify.annotations.Nullable;

/**
 * Finds the GrantForge access token of the user the current request is for. The default reads the request's
 * {@code Authorization: Bearer} header; an application that keeps tokens in its session provides its own.
 */
@FunctionalInterface
public interface AccessTokenResolver
{
    /**
     * Returns the current user's access token.
     *
     * @return the token, or {@code null} if the request has none
     */
    @Nullable String currentToken();
}
