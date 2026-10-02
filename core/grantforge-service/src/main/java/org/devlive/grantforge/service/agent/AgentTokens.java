// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.service.domain.AgentToken;
import org.devlive.grantforge.service.domain.AgentTokenRepository;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Issues, lists and revokes the tokens agents sign in with, and signs agents in. A token is {@value #PREFIX} followed
 * by 43 random characters; only its SHA-256 hash is stored. Signing in looks tokens up across tenants; everything else
 * must be called with the actor's tenant bound.
 */
@Service
public final class AgentTokens
{
    /** How every token starts. */
    public static final String PREFIX = "gfa_";

    /** How many characters of a token are kept to tell it apart. */
    static final int HINT_LENGTH = 10;

    /** How often a token's last use is written at most. */
    static final Duration USE_RESOLUTION = Duration.ofMinutes(1);

    private static final int TOKEN_BYTES = 32;

    private final AgentTokenRepository tokens;
    private final ManagedServiceRepository services;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates the service.
     *
     * @param tokens the agent tokens
     * @param services the services of the bound tenant
     * @param audit records issuing and revoking
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public AgentTokens(AgentTokenRepository tokens, ManagedServiceRepository services, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.tokens = requireNonNull(tokens, "tokens");
        this.services = requireNonNull(services, "services");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Issues a token for the agents of a service.
     *
     * @param actorId the account asking
     * @param serviceId the service
     * @param name what the token is for, 1 to 64 characters
     * @param expiresAt when it stops working, or {@code null} for never
     * @return the token with its secret, shown this once
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link CommonErrorCode#BAD_REQUEST} listing the
     *         inputs at fault
     */
    public IssuedToken issue(long actorId, long serviceId, String name, @Nullable Instant expiresAt)
    {
        String label = name.strip();
        Instant now = clock.instant();
        if (label.isEmpty() || label.length() > 64 || expiresAt != null && !expiresAt.isAfter(now)) {
            List<FieldIssue> issues = new ArrayList<>();
            if (label.isEmpty() || label.length() > 64) {
                issues.add(FieldIssue.of("name", "error.agent.token-name"));
            }
            if (expiresAt != null && !expiresAt.isAfter(now)) {
                issues.add(FieldIssue.of("expiresAt", "error.agent.token-expiry"));
            }
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "invalid agent token").withFieldIssues(issues);
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AgentTokenView view = requireNonNull(transactions.execute(status -> {
            if (!services.existsById(serviceId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + serviceId);
            }
            return view(tokens.saveAndFlush(AgentToken.create(serviceId, label, hash(secret), secret.substring(0, HINT_LENGTH), expiresAt)),
                    now);
        }));
        record(AuditAction.AGENT_TOKEN_ISSUED, actorId, view);
        return new IssuedToken(view, secret);
    }

    /**
     * Lists the tokens of a service.
     *
     * @param serviceId the service
     * @return the tokens, newest first
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public List<AgentTokenView> list(long serviceId)
    {
        Instant now = clock.instant();
        return requireNonNull(transactions.execute(status -> {
            if (!services.existsById(serviceId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + serviceId);
            }
            return tokens.findByServiceIdOrderByCreatedAtDescIdDesc(serviceId).stream().map(token -> view(token, now)).toList();
        }));
    }

    /**
     * Revokes a token: agents using it are refused from their next call on.
     *
     * @param actorId the account asking
     * @param tokenId the token
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void revoke(long actorId, long tokenId)
    {
        Instant now = clock.instant();
        AgentTokenView view = requireNonNull(transactions.execute(status -> {
            AgentToken token = tokens.findById(tokenId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no agent token " + tokenId));
            token.revoke(now);
            return view(tokens.saveAndFlush(token), now);
        }));
        record(AuditAction.AGENT_TOKEN_REVOKED, actorId, view);
    }

    /**
     * Signs an agent in.
     *
     * @param secret the token the agent presented
     * @return who the agent is, or empty for an unknown, revoked or expired token
     */
    public Optional<AgentCredential> authenticate(String secret)
    {
        if (!secret.startsWith(PREFIX) || secret.length() > 128) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        String hash = hash(secret);
        return TenantContext.callAsSystem(() -> transactions.execute(status -> tokens.findByTokenHash(hash)
                .filter(token -> token.isUsableAt(now))
                .map(token -> {
                    Instant last = token.getLastUsedAt();
                    if (last == null || !last.plus(USE_RESOLUTION).isAfter(now)) {
                        token.used(now);
                    }
                    return new AgentCredential(requireNonNull(token.getTenantId(), "tenant"), token.getServiceId(), token.requireId());
                })));
    }

    /**
     * Hashes a token as stored.
     *
     * @param secret the token
     * @return its SHA-256 hash, hexadecimal
     */
    static String hash(String secret)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static AgentTokenView view(AgentToken token, Instant now)
    {
        return new AgentTokenView(token.requireId(), token.getServiceId(), token.getName(), token.getTokenHint(),
                requireNonNull(token.getCreatedAt(), "createdAt"), token.getExpiresAt(), token.getRevokedAt(), token.getLastUsedAt(),
                token.isUsableAt(now));
    }

    private void record(AuditAction action, long actorId, AgentTokenView token)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(token.id()), token.name()));
    }
}
