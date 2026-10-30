// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import jakarta.validation.constraints.Size;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Provider settings as entered; blank values take the defaults of {@link OidcSettings}.
 *
 * @param issuer the issuer URL
 * @param clientId the client registered at the provider
 * @param scopes the scopes asked for
 * @param usernameClaim the claim of the user name
 * @param displayNameClaim the claim of the display name
 * @param emailClaim the claim of the e-mail address
 * @param authorizationParameters extra authorization request parameters
 */
public record OidcSettingsRequest(
        @Size(max = 512) @Nullable String issuer,
        @Size(max = 255) @Nullable String clientId,
        @Size(max = 512) @Nullable String scopes,
        @Size(max = 64) @Nullable String usernameClaim,
        @Size(max = 64) @Nullable String displayNameClaim,
        @Size(max = 64) @Nullable String emailClaim,
        @Nullable Map<String, String> authorizationParameters)
{
    /** Creates settings without extra parameters. */
    public OidcSettingsRequest(@Nullable String issuer, @Nullable String clientId, @Nullable String scopes, @Nullable String usernameClaim,
            @Nullable String displayNameClaim, @Nullable String emailClaim)
    {
        this(issuer, clientId, scopes, usernameClaim, displayNameClaim, emailClaim, null);
    }

    /**
     * Checks the settings and applies the defaults.
     *
     * @return the settings
     * @throws org.devlive.grantforge.common.error.GrantForgeException if they cannot work
     */
    @SuppressWarnings("NullAway") // OidcSettings treats missing values as blank ones
    public OidcSettings settings()
    {
        return new OidcSettings(issuer, clientId, scopes, usernameClaim, displayNameClaim, emailClaim, authorizationParameters);
    }
}
