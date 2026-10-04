// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import java.util.Locale;

/** A way an OAuth client may obtain tokens. */
public enum ClientGrant
{
    /** Signing users in through the browser and exchanging the code for tokens. */
    AUTHORIZATION_CODE,

    /** Renewing tokens without signing in again. */
    REFRESH_TOKEN,

    /** Obtaining tokens for the client itself, without a user; only for confidential clients. */
    CLIENT_CREDENTIALS;

    /**
     * Returns the grant type's name in OAuth, such as {@code authorization_code}.
     *
     * @return the name
     */
    public String oauthName()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
