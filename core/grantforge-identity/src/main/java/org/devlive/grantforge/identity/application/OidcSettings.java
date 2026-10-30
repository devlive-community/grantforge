// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * How to sign users in with an OpenID Connect provider (Keycloak, Azure AD, Okta, another GrantForge). The provider is
 * found by its issuer's discovery document; the client secret is the source's secret. Blank values take the defaults.
 *
 * @param issuer the issuer, an {@code https://} URL (or {@code http://} for local testing)
 * @param clientId the client registered at the provider, which sends users back to
 *         {@code /api/v1/auth/federated/callback/{code}}
 * @param scopes the scopes asked for, separated by spaces; {@code openid} is always asked
 * @param usernameClaim the claim that becomes the account's user name
 * @param displayNameClaim the claim that becomes the display name
 * @param emailClaim the claim that becomes the e-mail address
 * @param authorizationParameters extra authorization request parameters
 */
public record OidcSettings(
        String issuer,
        String clientId,
        String scopes,
        String usernameClaim,
        String displayNameClaim,
        String emailClaim,
        Map<String, String> authorizationParameters)
{
    private static final Set<String> RESERVED = Set.of("response_type", "client_id", "redirect_uri", "scope", "state", "nonce",
            "code_challenge", "code_challenge_method", "response_mode", "request", "request_uri");

    /** Creates settings without extra authorization parameters. */
    public OidcSettings(String issuer, String clientId, String scopes, String usernameClaim, String displayNameClaim, String emailClaim)
    {
        this(issuer, clientId, scopes, usernameClaim, displayNameClaim, emailClaim, null);
    }
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
        String scheme = schemeOf(text);
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
        if (authorizationParameters == null) {
            authorizationParameters = Map.of();
        }
        if (authorizationParameters.size() > 20 || authorizationParameters.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || !entry.getKey().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
                        || RESERVED.contains(entry.getKey().toLowerCase(java.util.Locale.ROOT)) || entry.getValue() == null
                        || entry.getValue().length() > 512)) {
            throw LdapSettings.invalid("invalid or reserved authorization parameter");
        }
        authorizationParameters = Map.copyOf(authorizationParameters);
    }

    private static String or(@Nullable String value, String fallback)
    {
        String text = Strings.blankToNull(value);
        return text == null ? fallback : text;
    }

    private static @Nullable String schemeOf(String url)
    {
        try {
            return URI.create(url).getScheme();
        }
        catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
