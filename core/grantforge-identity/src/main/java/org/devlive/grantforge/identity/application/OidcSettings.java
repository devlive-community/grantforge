// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * How to sign users in with an OpenID Connect provider (Keycloak, Azure AD, Okta, another GrantForge). The provider is
 * found by its issuer's discovery document; the client secret is the source's secret. Blank values take the defaults.
 *
 * @param issuer the issuer, an {@code https://} URL (or {@code http://} for local testing)
 * @param clientId the client registered at the provider, which sends users back to
 *         {@code /api/v1/auth/federated/callback/<code>}
 * @param scopes the scopes asked for, separated by spaces; {@code openid} is always asked
 * @param usernameClaim the claim that becomes the account's user name
 * @param displayNameClaim the claim that becomes the display name
 * @param emailClaim the claim that becomes the e-mail address
 */
public record OidcSettings(
        String issuer,
        String clientId,
        String scopes,
        String usernameClaim,
        String displayNameClaim,
        String emailClaim)
{
    /**
     * Applies the defaults and checks the values.
     *
     * @throws org.devlive.grantforge.common.error.GrantForgeException with {@link IdentityErrorCode#IDENTITY_SOURCE_INVALID}
     */
    public OidcSettings
    {
        String text = Strings.blankToNull(issuer);
        if (text == null) {
            throw LdapSettings.invalid("issuer is required");
        }
        String scheme;
        try {
            scheme = URI.create(text).getScheme();
        }
        catch (IllegalArgumentException malformed) {
            scheme = null;
        }
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw LdapSettings.invalid("issuer must be an https:// URL");
        }
        issuer = text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
        String client = Strings.blankToNull(clientId);
        if (client == null) {
            throw LdapSettings.invalid("clientId is required");
        }
        clientId = client;
        String asked = or(scopes, "openid profile email");
        scopes = asked.matches("(^|.*\\s)openid(\\s.*|$)") ? asked : "openid " + asked;
        usernameClaim = or(usernameClaim, "preferred_username");
        displayNameClaim = or(displayNameClaim, "name");
        emailClaim = or(emailClaim, "email");
    }

    private static String or(@Nullable String value, String fallback)
    {
        String text = Strings.blankToNull(value);
        return text == null ? fallback : text;
    }
}
