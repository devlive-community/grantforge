// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import com.nimbusds.jwt.JWTParser;
import org.devlive.grantforge.oauth.domain.AuthorizationContent;
import org.devlive.grantforge.oauth.domain.CodeRequest;
import org.devlive.grantforge.oauth.domain.IssuedToken;
import org.devlive.grantforge.oauth.domain.StoredAuthorization;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.security.Principal;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Converts between the authorization server's authorizations and what is stored of them. Only hashes of tokens are
 * stored, so an authorization read back carries the real value of the token it was looked up by and placeholders for the
 * others; claims come from the token itself when it is a JWT. Attributes are stored field by field: the sign-in as its
 * account, the authorization request as its redirect URI, PKCE challenge and nonce. An attribute of another kind is a
 * programming error, as flows that would add one are not enabled.
 */
final class AuthorizationCodec
{
    /** Attribute holding the stored version, to detect two requests changing the same authorization. */
    static final String VERSION = AuthorizationCodec.class.getName() + ".version";

    /** Longest state and nonce stored; requests with longer ones are refused. */
    static final int MAX_PARAMETER = 255;

    /** Longest redirect URI, as long as clients may register. */
    static final int MAX_URI = 512;

    private static final String PRINCIPAL = Principal.class.getName();
    private static final String REQUEST = OAuth2AuthorizationRequest.class.getName();
    private static final Set<String> KNOWN = Set.of(PRINCIPAL, REQUEST, VERSION);
    private static final Set<String> TIME_CLAIMS = Set.of("exp", "iat", "nbf");
    private static final String AUTH_TIME = "auth_time";

    private final OAuthPrincipals principals;

    /**
     * Creates the codec.
     *
     * @param principals translates sign-ins
     */
    AuthorizationCodec(OAuthPrincipals principals)
    {
        this.principals = requireNonNull(principals, "principals");
    }

    /**
     * Describes an authorization for storage.
     *
     * @param authorization the authorization
     * @param now the current time, the expiry of an authorization without tokens
     * @return what is stored
     * @throws OAuth2AuthenticationException with {@code invalid_request} if the request carries a state, nonce or redirect URI
     *         too long to store
     */
    AuthorizationContent content(OAuth2Authorization authorization, Instant now)
    {
        for (String name : authorization.getAttributes().keySet()) {
            if (!KNOWN.contains(name)) {
                throw new IllegalStateException("cannot store authorization attribute " + name);
            }
        }
        Authentication signedIn = authorization.getAttribute(PRINCIPAL);
        Optional<OAuthSubject> subject = signedIn == null ? Optional.empty() : principals.subjectOf(signedIn);
        OAuth2AuthorizationRequest request = authorization.getAttribute(REQUEST);
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization.getAccessToken();
        IssuedToken code = slot(authorization.getToken(OAuth2AuthorizationCode.class));
        IssuedToken accessToken = slot(access);
        IssuedToken refresh = slot(authorization.getRefreshToken());
        IssuedToken idToken = slot(authorization.getToken(OidcIdToken.class));
        Instant expiresAt = Stream.of(code, accessToken, refresh, idToken).map(IssuedToken::getExpiresAt).filter(Objects::nonNull)
                .max(Instant::compareTo).orElse(now);
        return new AuthorizationContent(authorization.getRegisteredClientId(), authorization.getPrincipalName(),
                subject.map(OAuthSubject::accountId).orElse(null), subject.map(OAuthSubject::tenantId).orElse(null),
                subject.map(OAuthSubject::username).orElse(null), subject.map(OAuthSubject::authenticatedAt).orElse(null),
                authorization.getAuthorizationGrantType().getValue(),
                authorization.getAuthorizedScopes(), request == null ? null : request(request), code, accessToken,
                access == null ? Set.of() : access.getToken().getScopes(), refresh, idToken, expiresAt);
    }

    /**
     * Rebuilds an authorization.
     *
     * @param stored the stored authorization
     * @param client its client
     * @param presented the token it was looked up by, or {@code null}
     * @param slot which of its tokens that is, or {@code null}
     * @return the authorization
     */
    OAuth2Authorization authorization(StoredAuthorization stored, RegisteredClient client, @Nullable String presented,
            @Nullable Slot slot)
    {
        AuthorizationContent content = stored.content();
        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(client)
                .id(stored.getAuthorizationId())
                .principalName(content.principalName())
                .authorizationGrantType(new AuthorizationGrantType(content.grantType()))
                .authorizedScopes(content.authorizedScopes())
                .attribute(VERSION, requireNonNull(stored.getVersion(), "version"));
        Long accountId = content.accountId();
        Long tenantId = content.tenantId();
        String username = content.username();
        Instant authenticatedAt = content.authenticatedAt();
        if (accountId != null && tenantId != null && username != null && authenticatedAt != null) {
            builder.attribute(PRINCIPAL, principals.authenticationOf(new OAuthSubject(accountId, tenantId, username, authenticatedAt)));
        }
        CodeRequest request = content.request();
        if (request != null) {
            builder.attribute(REQUEST, request(request, client));
        }
        add(builder, content.code(), Slot.CODE, presented, slot,
                (value, token) -> new OAuth2AuthorizationCode(value, requireNonNull(token.getIssuedAt(), "issuedAt"),
                        requireNonNull(token.getExpiresAt(), "expiresAt")), null);
        add(builder, content.access(), Slot.ACCESS, presented, slot,
                (value, token) -> new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value, token.getIssuedAt(), token.getExpiresAt(),
                        content.accessScopes()), presentedClaims(presented, slot, Slot.ACCESS));
        add(builder, content.refresh(), Slot.REFRESH, presented, slot,
                (value, token) -> new OAuth2RefreshToken(value, token.getIssuedAt(), token.getExpiresAt()), null);
        Map<String, Object> presentedId = presentedClaims(presented, slot, Slot.ID_TOKEN);
        Map<String, Object> idClaims = presentedId.isEmpty() ? knownIdClaims(content, client) : presentedId;
        add(builder, content.idToken(), Slot.ID_TOKEN, presented, slot,
                (value, token) -> new OidcIdToken(value, requireNonNull(token.getIssuedAt(), "issuedAt"),
                        requireNonNull(token.getExpiresAt(), "expiresAt"), idClaims), idClaims);
        return builder.build();
    }

    /**
     * What is known of an ID token without its value: whom it is about, for which client, and when the account signed in,
     * which a refresh copies into the next ID token.
     */
    private static Map<String, Object> knownIdClaims(AuthorizationContent content, RegisteredClient client)
    {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", content.principalName());
        claims.put("aud", List.of(client.getClientId()));
        Instant authenticatedAt = content.authenticatedAt();
        if (authenticatedAt != null) {
            // The authorization server reads auth_time as a Date.
            claims.put(AUTH_TIME, Date.from(authenticatedAt));
        }
        return claims;
    }

    private static CodeRequest request(OAuth2AuthorizationRequest request)
    {
        Map<String, Object> parameters = request.getAdditionalParameters();
        String state = limited(request.getState(), MAX_PARAMETER, OAuth2ParameterNames.STATE);
        String nonce = limited(text(parameters.get(OidcParameterNames.NONCE)), MAX_PARAMETER, OidcParameterNames.NONCE);
        String redirect = limited(request.getRedirectUri(), MAX_URI, OAuth2ParameterNames.REDIRECT_URI);
        return new CodeRequest(request.getAuthorizationUri(), redirect, request.getScopes(), state,
                text(parameters.get(PkceParameterNames.CODE_CHALLENGE)), text(parameters.get(PkceParameterNames.CODE_CHALLENGE_METHOD)), nonce);
    }

    private static OAuth2AuthorizationRequest request(CodeRequest request, RegisteredClient client)
    {
        Map<String, Object> parameters = new LinkedHashMap<>();
        put(parameters, PkceParameterNames.CODE_CHALLENGE, request.codeChallenge());
        put(parameters, PkceParameterNames.CODE_CHALLENGE_METHOD, request.codeChallengeMethod());
        put(parameters, OidcParameterNames.NONCE, request.nonce());
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(request.authorizationUri())
                .clientId(client.getClientId())
                .redirectUri(request.redirectUri())
                .scopes(request.scopes())
                .state(request.state())
                .additionalParameters(parameters)
                .build();
    }

    private static <T extends AbstractOAuth2Token> void add(OAuth2Authorization.Builder builder, IssuedToken token, Slot kind,
            @Nullable String presented, @Nullable Slot slot, TokenFactory<T> factory, @Nullable Map<String, Object> claims)
    {
        String hash = token.getHash();
        if (hash == null) {
            return;
        }
        String value = kind == slot && presented != null ? presented : TokenHashes.placeholder(hash);
        Consumer<Map<String, Object>> metadata = values -> {
            values.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, token.isInvalidated());
            if (claims != null && !claims.isEmpty()) {
                values.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claims);
            }
        };
        builder.token(factory.create(value, token), metadata);
    }

    /** The claims of the token presented in a slot, if it is a JWT; empty for another slot, no token or an opaque one. */
    private static Map<String, Object> presentedClaims(@Nullable String presented, @Nullable Slot slot, Slot kind)
    {
        if (presented == null || slot != kind) {
            return Map.of();
        }
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            JWTParser.parse(presented).getJWTClaimsSet().getClaims()
                    .forEach((name, value) -> claims.put(name, value instanceof Date date && TIME_CLAIMS.contains(name) ? date.toInstant() : value));
            return claims;
        }
        catch (ParseException notJwt) {
            return Map.of();
        }
    }

    private static <T extends AbstractOAuth2Token> IssuedToken slot(OAuth2Authorization.@Nullable Token<T> token)
    {
        if (token == null) {
            return IssuedToken.none();
        }
        T value = token.getToken();
        return new IssuedToken(TokenHashes.of(value.getTokenValue()), value.getIssuedAt(), value.getExpiresAt(), token.isInvalidated());
    }

    private static @Nullable String limited(@Nullable String value, int max, String parameter)
    {
        if (value != null && value.length() > max) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                    "OAuth 2.0 Parameter: " + parameter + " is longer than " + max + " characters", null));
        }
        return value;
    }

    private static @Nullable String text(@Nullable Object value)
    {
        return value == null ? null : value.toString();
    }

    private static void put(Map<String, Object> parameters, String name, @Nullable String value)
    {
        if (value != null) {
            parameters.put(name, value);
        }
    }

    /** Which token of an authorization a lookup found. */
    enum Slot
    {
        /** The authorization code. */
        CODE,
        /** The access token. */
        ACCESS,
        /** The refresh token. */
        REFRESH,
        /** The OpenID Connect ID token. */
        ID_TOKEN
    }

    /** Makes a token of a slot from its value and stored times. */
    @FunctionalInterface
    private interface TokenFactory<T extends AbstractOAuth2Token>
    {
        T create(String value, IssuedToken token);
    }
}
