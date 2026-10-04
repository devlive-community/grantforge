// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.application.ClientAccessEnded;
import org.devlive.grantforge.oauth.application.AuthorizationCodec.Slot;
import org.devlive.grantforge.oauth.domain.AuthorizationContent;
import org.devlive.grantforge.oauth.domain.IssuedToken;
import org.devlive.grantforge.oauth.domain.RetiredToken;
import org.devlive.grantforge.oauth.domain.RetiredTokenRepository;
import org.devlive.grantforge.oauth.domain.StoredAuthorization;
import org.devlive.grantforge.oauth.domain.StoredAuthorizationRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Where the authorization server keeps authorizations: in the database, with tokens as hashes. Each refresh replaces the
 * refresh token and retires the old one; presenting a retired token again revokes the whole authorization, as the token
 * was stolen or replayed (OAuth 2.1, section 4.3.1). Two requests changing the same authorization at once cannot both
 * succeed. An authorization whose client was deleted or disabled is not found.
 */
@Component
public final class StoredAuthorizations
        implements OAuth2AuthorizationService
{
    private static final Logger LOG = LoggerFactory.getLogger(StoredAuthorizations.class);
    private static final String CODE = OAuth2ParameterNames.CODE;
    private static final String ACCESS = OAuth2TokenType.ACCESS_TOKEN.getValue();
    private static final String REFRESH = OAuth2TokenType.REFRESH_TOKEN.getValue();
    private static final String ID_TOKEN = OidcParameterNames.ID_TOKEN;

    private final StoredAuthorizationRepository authorizations;
    private final RetiredTokenRepository retired;
    private final CatalogClients clients;
    private final AuthorizationCodec codec;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param authorizations the stored authorizations
     * @param retired refresh tokens replaced by rotations
     * @param clients the clients authorizations belong to
     * @param principals translates sign-ins
     * @param audit records sign-ins to clients and replays
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public StoredAuthorizations(StoredAuthorizationRepository authorizations, RetiredTokenRepository retired, CatalogClients clients,
            OAuthPrincipals principals, AuditLog audit, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.authorizations = requireNonNull(authorizations, "authorizations");
        this.retired = requireNonNull(retired, "retired");
        this.clients = requireNonNull(clients, "clients");
        this.codec = new AuthorizationCodec(requireNonNull(principals, "principals"));
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Stores an authorization.
     *
     * @param authorization the authorization, new or changed
     * @throws OAuth2AuthenticationException with {@code invalid_grant} if another request changed it since it was read
     */
    @Override
    public void save(OAuth2Authorization authorization)
    {
        Instant now = clock.instant();
        AuthorizationContent content = codec.content(authorization, now);
        transactions.executeWithoutResult(status -> {
            StoredAuthorization stored = authorizations.findByAuthorizationId(authorization.getId()).orElse(null);
            if (stored != null) {
                Long read = authorization.getAttribute(AuthorizationCodec.VERSION);
                if (read != null && !read.equals(stored.getVersion())) {
                    throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                            "The authorization was changed by another request", null));
                }
                retire(stored.getRefresh(), content.refresh(), stored.getAuthorizationId(), now);
                stored.replace(content);
                authorizations.saveAndFlush(stored);
            }
            else {
                authorizations.saveAndFlush(StoredAuthorization.create(authorization.getId(), content));
                if (content.code().isPresent() && content.accountId() != null) {
                    audit.recordWithChange(new AuditRecord(AuditAction.OAUTH_AUTHORIZED, AuditOutcome.SUCCESS, content.tenantId(),
                            content.accountId(), content.username(), clientIdOf(content.registeredClientId()),
                            String.join(" ", content.authorizedScopes())));
                }
            }
        });
    }

    @Override
    public void remove(OAuth2Authorization authorization)
    {
        transactions.executeWithoutResult(status -> authorizations.findByAuthorizationId(authorization.getId()).ifPresent(this::revoke));
    }

    @Override
    public @Nullable OAuth2Authorization findById(String id)
    {
        return requireNonNull(transactions.execute(status -> authorizations.findByAuthorizationId(id)
                .flatMap(stored -> rebuilt(stored, null, null)))).orElse(null);
    }

    /**
     * Finds the authorization holding a token. A refresh token that a rotation already replaced revokes its authorization
     * and finds nothing.
     *
     * @param token the token presented
     * @param tokenType its type, or {@code null} to try every type
     * @return the authorization, or {@code null}
     */
    @Override
    public @Nullable OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType)
    {
        if (token.startsWith(TokenHashes.UNKNOWN)) {
            return null;
        }
        String hash = TokenHashes.of(token);
        String type = tokenType == null ? null : tokenType.getValue();
        return requireNonNull(transactions.execute(status -> {
            for (Slot slot : Slot.values()) {
                if (type == null || type.equals(name(slot))) {
                    StoredAuthorization found = lookup(slot, hash).orElse(null);
                    if (found != null) {
                        return rebuilt(found, token, slot);
                    }
                }
            }
            if (type == null || REFRESH.equals(type)) {
                retired.findByTokenHash(hash).ifPresent(this::replayed);
            }
            return Optional.<OAuth2Authorization>empty();
        })).orElse(null);
    }

    /**
     * Drops what was issued to a client that was deleted or disabled, in the transaction of that change.
     *
     * @param ended the client
     */
    @EventListener
    public void clientAccessEnded(ClientAccessEnded ended)
    {
        int removed = authorizations.deleteByClient(Long.toString(ended.id()));
        LOG.info("Revoked {} authorizations of OAuth client '{}'", removed, ended.clientId());
    }

    /**
     * Deletes authorizations and retired tokens past their expiry.
     *
     * @param now the current time
     * @return how many authorizations went
     */
    int purgeExpired(Instant now)
    {
        return requireNonNull(transactions.execute(status -> {
            retired.deleteExpired(now);
            return authorizations.deleteExpired(now);
        }));
    }

    private Optional<StoredAuthorization> lookup(Slot slot, String hash)
    {
        return switch (slot) {
            case CODE -> authorizations.findByCodeHash(hash);
            case ACCESS -> authorizations.findByAccessHash(hash);
            case REFRESH -> authorizations.findByRefreshHash(hash);
            case ID_TOKEN -> authorizations.findByIdTokenHash(hash);
        };
    }

    private static String name(Slot slot)
    {
        return switch (slot) {
            case CODE -> CODE;
            case ACCESS -> ACCESS;
            case REFRESH -> REFRESH;
            case ID_TOKEN -> ID_TOKEN;
        };
    }

    private Optional<OAuth2Authorization> rebuilt(StoredAuthorization stored, @Nullable String presented, @Nullable Slot slot)
    {
        return Optional.ofNullable(clients.findById(stored.content().registeredClientId()))
                .map(client -> codec.authorization(stored, client, presented, slot));
    }

    private void retire(IssuedToken before, IssuedToken after, String authorizationId, Instant now)
    {
        String old = before.getHash();
        if (old != null && !old.equals(after.getHash())) {
            retired.save(RetiredToken.of(old, authorizationId, requireNonNullElse(before.getExpiresAt(), now)));
        }
    }

    private void replayed(RetiredToken token)
    {
        authorizations.findByAuthorizationId(token.getAuthorizationId()).ifPresent(stored -> {
            AuthorizationContent content = stored.content();
            LOG.warn("A replaced refresh token of OAuth client '{}' was presented again; revoking its authorization",
                    clientIdOf(content.registeredClientId()));
            revoke(stored);
            audit.recordWithChange(new AuditRecord(AuditAction.OAUTH_TOKEN_REPLAYED, AuditOutcome.FAILURE, content.tenantId(),
                    content.accountId(), content.username(), clientIdOf(content.registeredClientId()), null));
        });
    }

    private void revoke(StoredAuthorization stored)
    {
        retired.deleteByAuthorization(stored.getAuthorizationId());
        authorizations.delete(stored);
    }

    private String clientIdOf(String registeredClientId)
    {
        RegisteredClient client = clients.findById(registeredClientId);
        return client == null ? registeredClientId : Objects.requireNonNullElse(client.getClientId(), registeredClientId);
    }
}
