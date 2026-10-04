// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;

/**
 * An authorization of the authorization server: what a client was granted, for whom, and the tokens it holds, as hashes.
 * Its version guards against two requests rotating the same refresh token at once.
 */
@Entity
@Table(name = "gf_oauth_authorization")
public class StoredAuthorization
        extends BaseEntity
{
    @Column(name = "authorization_id", nullable = false, updatable = false, length = 64)
    private String authorizationId = "";

    @Column(name = "registered_client_id", nullable = false, length = 64)
    private String registeredClientId = "";

    @Column(name = "principal_name", nullable = false, length = 128)
    private String principalName = "";

    @Column(name = "account_id")
    private @Nullable Long accountId;

    @Column(name = "tenant_id")
    private @Nullable Long tenantId;

    @Column(name = "username", length = 64)
    private @Nullable String username;

    @Column(name = "authenticated_at")
    private @Nullable Instant authenticatedAt;

    @Column(name = "grant_type", nullable = false, length = 64)
    private String grantType = "";

    @Column(name = "authorized_scopes", nullable = false, length = 500)
    private String authorizedScopes = "";

    @Column(name = "request_uri", length = 512)
    private @Nullable String requestUri;

    @Column(name = "request_redirect_uri", length = 512)
    private @Nullable String requestRedirectUri;

    @Column(name = "request_scopes", length = 500)
    private @Nullable String requestScopes;

    @Column(name = "request_state", length = 255)
    private @Nullable String requestState;

    @Column(name = "code_challenge", length = 128)
    private @Nullable String codeChallenge;

    @Column(name = "code_challenge_method", length = 16)
    private @Nullable String codeChallengeMethod;

    @Column(name = "nonce", length = 255)
    private @Nullable String nonce;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "hash", column = @Column(name = "code_hash", length = 64)),
            @AttributeOverride(name = "issuedAt", column = @Column(name = "code_issued_at")),
            @AttributeOverride(name = "expiresAt", column = @Column(name = "code_expires_at")),
            @AttributeOverride(name = "invalidated", column = @Column(name = "code_invalidated", nullable = false))})
    private @Nullable IssuedToken code;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "hash", column = @Column(name = "access_hash", length = 64)),
            @AttributeOverride(name = "issuedAt", column = @Column(name = "access_issued_at")),
            @AttributeOverride(name = "expiresAt", column = @Column(name = "access_expires_at")),
            @AttributeOverride(name = "invalidated", column = @Column(name = "access_invalidated", nullable = false))})
    private @Nullable IssuedToken access;

    @Column(name = "access_scopes", length = 500)
    private @Nullable String accessScopes;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "hash", column = @Column(name = "refresh_hash", length = 64)),
            @AttributeOverride(name = "issuedAt", column = @Column(name = "refresh_issued_at")),
            @AttributeOverride(name = "expiresAt", column = @Column(name = "refresh_expires_at")),
            @AttributeOverride(name = "invalidated", column = @Column(name = "refresh_invalidated", nullable = false))})
    private @Nullable IssuedToken refresh;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "hash", column = @Column(name = "id_token_hash", length = 64)),
            @AttributeOverride(name = "issuedAt", column = @Column(name = "id_token_issued_at")),
            @AttributeOverride(name = "expiresAt", column = @Column(name = "id_token_expires_at")),
            @AttributeOverride(name = "invalidated", column = @Column(name = "id_token_invalidated", nullable = false))})
    private @Nullable IssuedToken idToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt = Instant.EPOCH;

    /** For JPA. */
    protected StoredAuthorization()
    {
    }

    /**
     * Stores a new authorization.
     *
     * @param authorizationId the authorization server's identifier
     * @param content what it holds
     * @return the authorization
     */
    public static StoredAuthorization create(String authorizationId, AuthorizationContent content)
    {
        StoredAuthorization authorization = new StoredAuthorization();
        authorization.authorizationId = requireNonNull(authorizationId, "authorizationId");
        authorization.replace(content);
        return authorization;
    }

    /**
     * Replaces what the authorization holds, as after a code exchange or a refresh.
     *
     * @param content what it holds now
     */
    // Without a pending code request its columns are NULL.
    @SuppressWarnings("PMD.NullAssignment")
    public final void replace(AuthorizationContent content)
    {
        registeredClientId = content.registeredClientId();
        principalName = content.principalName();
        accountId = content.accountId();
        tenantId = content.tenantId();
        username = content.username();
        authenticatedAt = content.authenticatedAt();
        grantType = content.grantType();
        authorizedScopes = join(content.authorizedScopes());
        CodeRequest request = content.request();
        requestUri = request == null ? null : request.authorizationUri();
        requestRedirectUri = request == null ? null : request.redirectUri();
        requestScopes = request == null ? null : join(request.scopes());
        requestState = request == null ? null : request.state();
        codeChallenge = request == null ? null : request.codeChallenge();
        codeChallengeMethod = request == null ? null : request.codeChallengeMethod();
        nonce = request == null ? null : request.nonce();
        code = content.code();
        access = content.access();
        accessScopes = join(content.accessScopes());
        refresh = content.refresh();
        idToken = content.idToken();
        expiresAt = content.expiresAt();
    }

    /**
     * Returns what the authorization holds.
     *
     * @return the content
     */
    public AuthorizationContent content()
    {
        String uri = requestUri;
        CodeRequest request = uri == null ? null : new CodeRequest(uri, requestRedirectUri, split(requestScopes), requestState,
                codeChallenge, codeChallengeMethod, nonce);
        return new AuthorizationContent(registeredClientId, principalName, accountId, tenantId, username, authenticatedAt, grantType,
                split(authorizedScopes), request, slot(code), slot(access), split(accessScopes), slot(refresh), slot(idToken), expiresAt);
    }

    /**
     * Returns the authorization server's identifier.
     *
     * @return the identifier
     */
    public String getAuthorizationId()
    {
        return authorizationId;
    }

    /**
     * Returns the current refresh token, if any.
     *
     * @return the slot
     */
    public IssuedToken getRefresh()
    {
        return slot(refresh);
    }

    private static IssuedToken slot(@Nullable IssuedToken token)
    {
        return requireNonNullElseGet(token, IssuedToken::none);
    }

    private static String join(Set<String> values)
    {
        return String.join(" ", new LinkedHashSet<>(values));
    }

    private static Set<String> split(@Nullable String values)
    {
        return values == null || values.isBlank() ? Set.of()
                : Arrays.stream(values.split(" ")).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
