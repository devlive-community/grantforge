// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Keeps none of the tokens identity providers issue at sign-in: GrantForge needs the ID token once, to know who signed
 * in, and never calls the provider on the user's behalf, so their access and refresh tokens are not stored in sessions.
 */
final class NoAuthorizedClients
        implements OAuth2AuthorizedClientRepository
{
    @Override
    public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(String clientRegistrationId, Authentication principal,
            HttpServletRequest request)
    {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal, HttpServletRequest request,
            HttpServletResponse response)
    {
        // Nothing to keep.
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal, HttpServletRequest request,
            HttpServletResponse response)
    {
        // Nothing kept.
    }
}
