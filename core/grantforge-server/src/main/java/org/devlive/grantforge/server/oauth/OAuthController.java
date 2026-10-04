// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.oauth.application.OAuthProperties;
import org.devlive.grantforge.oauth.application.SigningKeys;
import org.devlive.grantforge.server.security.SessionUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;

/** The authorization server as platform administrators see it: its issuer and discovery document, and its signing keys. */
@RestController
@RequestMapping("/api/v1/oauth")
public final class OAuthController
{
    private final SigningKeys keys;
    private final OAuthProperties properties;

    /**
     * Creates the controller.
     *
     * @param keys the signing keys
     * @param properties the configured issuer
     */
    public OAuthController(SigningKeys keys, OAuthProperties properties)
    {
        this.keys = requireNonNull(keys, "keys");
        this.properties = requireNonNull(properties, "properties");
    }

    /**
     * Describes the authorization server.
     *
     * @param request the request, whose URL is the issuer unless one is configured
     * @return the issuer, discovery URL and keys
     */
    @RequirePermission("platform.oauth.read")
    @GetMapping
    public OAuthServerResponse oauthServer(HttpServletRequest request)
    {
        String issuer = requireNonNullElseGet(properties.issuer(), () -> ServletUriComponentsBuilder.fromContextPath(request).toUriString());
        return new OAuthServerResponse(issuer, issuer + "/.well-known/openid-configuration",
                keys.list().stream().map(OAuthSigningKeyResponse::from).toList());
    }

    /**
     * Begins signing with a new key; the current one stays published until the tokens it signed have expired.
     *
     * @param user the session's principal
     * @return the new key
     */
    @RequireStepUp
    @RequirePermission("platform.oauth.rotate")
    @PostMapping("/signing-keys/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    public OAuthSigningKeyResponse rotateSigningKey(@AuthenticationPrincipal SessionUser user)
    {
        return OAuthSigningKeyResponse.from(keys.rotate(user.accountId()));
    }
}
