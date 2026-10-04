// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * The catalog's OAuth clients as the authorization server sees them. A disabled or deleted client is not found, so it
 * obtains nothing more. Every client must use PKCE, is asked no consent (platform administrators register only trusted
 * applications), and gets a new refresh token on every refresh.
 */
@Component
public final class CatalogClients
        implements RegisteredClientRepository
{
    /** How long an authorization code may wait to be exchanged. */
    static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final OAuthClientRepository clients;
    private final Clock clock;

    /**
     * Creates the repository.
     *
     * @param clients the catalog's clients
     * @param clock the current time, for secrets still in their grace period
     */
    public CatalogClients(OAuthClientRepository clients, Clock clock)
    {
        this.clients = requireNonNull(clients, "clients");
        this.clock = requireNonNull(clock, "clock");
    }

    /** Clients are registered in the console, never by the authorization server. */
    @Override
    public void save(RegisteredClient registeredClient)
    {
        throw new UnsupportedOperationException("OAuth clients are registered in the console");
    }

    @Override
    public @Nullable RegisteredClient findById(String id)
    {
        long record;
        try {
            record = Long.parseLong(id);
        }
        catch (NumberFormatException notOurs) {
            return null;
        }
        return clients.findById(record).map(this::registered).orElse(null);
    }

    @Override
    public @Nullable RegisteredClient findByClientId(String clientId)
    {
        return clients.findByClientId(clientId).map(this::registered).orElse(null);
    }

    private @Nullable RegisteredClient registered(OAuthClient client)
    {
        if (!client.isEnabled()) {
            return null;
        }
        Instant now = clock.instant();
        RegisteredClient.Builder builder = RegisteredClient.withId(Long.toString(client.requireId()))
                .clientId(client.getClientId())
                .clientIdIssuedAt(requireNonNullElse(client.getCreatedAt(), now))
                .clientName(client.getName())
                .redirectUris(uris -> uris.addAll(client.getRedirectUris()))
                .scopes(scopes -> scopes.addAll(client.getScopes()))
                .authorizationGrantTypes(grants -> client.getGrants().forEach(grant -> grants.add(grant(grant))))
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(CODE_TTL)
                        .accessTokenTimeToLive(client.getAccessTokenTtl())
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .refreshTokenTimeToLive(client.getRefreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build());
        String secret = client.getSecretHash();
        if (client.getType() == ClientType.CONFIDENTIAL && secret != null) {
            builder.clientSecret(ClientSecrets.join(secret, client.previousSecretHash(now)))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        }
        else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        return builder.build();
    }

    private static AuthorizationGrantType grant(ClientGrant grant)
    {
        return switch (grant) {
            case AUTHORIZATION_CODE -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case REFRESH_TOKEN -> AuthorizationGrantType.REFRESH_TOKEN;
            case CLIENT_CREDENTIALS -> AuthorizationGrantType.CLIENT_CREDENTIALS;
        };
    }
}
