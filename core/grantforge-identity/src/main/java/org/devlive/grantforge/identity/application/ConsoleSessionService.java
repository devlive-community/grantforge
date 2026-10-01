// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.ConsoleSession;
import org.devlive.grantforge.identity.domain.ConsoleSessionEntry;
import org.devlive.grantforge.identity.domain.ConsoleSessionRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Lists and ends console sessions. Every method must be called with the session owner's tenant bound.
 *
 * <p>Until roles exist, only system accounts (the administrator first-run setup creates) may see or end other
 * people's sessions; everyone may see and end their own.
 */
@Service
public final class ConsoleSessionService
{
    private final ConsoleSessionRepository sessions;
    private final UserAccountRepository accounts;
    private final SessionTerminator terminator;
    private final SessionProperties properties;
    private final Duration retention;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param sessions the session index
     * @param accounts user accounts
     * @param terminator ends sessions in the session store
     * @param properties session settings
     * @param idleTimeout how long an unused session stays valid ({@code spring.session.timeout})
     * @param transactionManager opens transactions
     * @param clock source of the current time
     */
    public ConsoleSessionService(ConsoleSessionRepository sessions, UserAccountRepository accounts,
            SessionTerminator terminator, SessionProperties properties,
            @Value("${spring.session.timeout:30m}") Duration idleTimeout, PlatformTransactionManager transactionManager,
            Clock clock)
    {
        this.sessions = requireNonNull(sessions, "sessions");
        this.accounts = requireNonNull(accounts, "accounts");
        this.terminator = requireNonNull(terminator, "terminator");
        this.properties = requireNonNull(properties, "properties");
        // Last activity is written at most once per interval, so a session may have been used up to one interval
        // after its recorded time; keep it listed until it has surely expired.
        this.retention = requireNonNull(idleTimeout, "idleTimeout").plus(properties.activityInterval());
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Indexes a session that just signed in, forgets the tenant's expired sessions, and ends the account's least
     * recently used sessions beyond {@link SessionProperties#maxPerAccount()}.
     *
     * @param sessionId Spring Session's ID of the new session
     * @param accountId the signed-in account
     * @param clientIp the client's address, if known
     * @param userAgent the client's user agent, if known
     */
    public void start(String sessionId, long accountId, @Nullable String clientIp, @Nullable String userAgent)
    {
        Instant now = clock.instant();
        List<ConsoleSession> surplus = requireNonNull(transactions.execute(status -> {
            sessions.deleteExpired(now.minus(retention));
            sessions.save(ConsoleSession.start(sessionId, accountId, clientIp, userAgent, now));
            int max = properties.maxPerAccount();
            if (max == 0) {
                return List.<ConsoleSession>of();
            }
            List<ConsoleSession> others = sessions
                    .findByAccountIdAndLastSeenAtAfterOrderByLastSeenAtDescIdDesc(accountId, now.minus(retention))
                    .stream().filter(session -> !session.getSessionId().equals(sessionId)).toList();
            // The new session counts towards the limit, so max - 1 of the others survive.
            return others.size() < max ? List.<ConsoleSession>of() : others.subList(max - 1, others.size());
        }));
        surplus.forEach(session -> end(session, sessionId));
    }

    /**
     * Records that a session was used. A session missing from the index (signed in before the index existed, or
     * forgotten as expired while still valid) is indexed again.
     *
     * @param sessionId Spring Session's ID
     * @param accountId the session's account
     * @param clientIp the client's address, if known
     * @param userAgent the client's user agent, if known
     */
    public void touch(String sessionId, long accountId, @Nullable String clientIp, @Nullable String userAgent)
    {
        Instant now = clock.instant();
        Integer updated = transactions.execute(status -> sessions.touch(sessionId, now));
        if (updated != null && updated > 0) {
            return;
        }
        try {
            transactions.executeWithoutResult(status ->
                    sessions.save(ConsoleSession.start(sessionId, accountId, clientIp, userAgent, now)));
        }
        catch (DataIntegrityViolationException ignored) {
            // Another request of the same session indexed it first.
        }
    }

    /**
     * Forgets a session that signed out.
     *
     * @param sessionId Spring Session's ID
     */
    public void forget(String sessionId)
    {
        transactions.executeWithoutResult(status -> sessions.deleteBySessionId(sessionId));
    }

    /**
     * Lists an account's active sessions, most recently used first.
     *
     * @param accountId the account
     * @param currentSessionId the session of the request, marked as current
     * @return the sessions
     */
    public List<ActiveSession> listOwn(long accountId, @Nullable String currentSessionId)
    {
        Instant since = clock.instant().minus(retention);
        return requireNonNull(transactions.execute(status -> accounts.findById(accountId)
                .map(account -> sessions.findByAccountIdAndLastSeenAtAfterOrderByLastSeenAtDescIdDesc(accountId, since)
                        .stream().map(session -> view(session, account.getUsername(), account.getDisplayName(),
                                currentSessionId)).toList())
                .orElse(List.of())));
    }

    /**
     * Lists the tenant's active sessions, most recently used first.
     *
     * @param actorId the account asking
     * @param page the page
     * @param currentSessionId the session of the request, marked as current
     * @return the sessions
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} if the actor may not manage sessions
     */
    public PageResult<ActiveSession> listAll(long actorId, PageQuery page, @Nullable String currentSessionId)
    {
        Instant since = clock.instant().minus(retention);
        return requireNonNull(transactions.execute(status -> {
            requireAdministrator(actorId);
            Page<ConsoleSessionEntry> found = sessions.findActive(since,
                    PageRequest.of(page.page() - 1, page.size()));
            List<ActiveSession> items = found.getContent().stream().map(entry -> view(entry.session(), entry.username(),
                    entry.displayName(), currentSessionId)).toList();
            return new PageResult<>(items, page.page(), page.size(), found.getTotalElements());
        }));
    }

    /**
     * Ends one of the actor's own sessions.
     *
     * @param actorId the account asking
     * @param id the session's handle
     * @param currentSessionId the session of the request
     * @return whether the ended session is the request's own; the caller must then invalidate it, because the
     *         request still holds it and would store it again
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} if the actor has no such session
     */
    public boolean revokeOwn(long actorId, long id, @Nullable String currentSessionId)
    {
        ConsoleSession session = requireNonNull(transactions.execute(status -> sessions.findById(id)
                .filter(found -> found.getAccountId() == actorId)
                .orElseThrow(() -> notFound(id))));
        return end(session, currentSessionId);
    }

    /**
     * Ends any session of the tenant.
     *
     * @param actorId the account asking
     * @param id the session's handle
     * @param currentSessionId the session of the request
     * @return whether the ended session is the request's own; the caller must then invalidate it
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} if the actor may not manage sessions, or
     *         {@link CommonErrorCode#NOT_FOUND} if the tenant has no such session
     */
    public boolean revoke(long actorId, long id, @Nullable String currentSessionId)
    {
        ConsoleSession session = requireNonNull(transactions.execute(status -> {
            requireAdministrator(actorId);
            return sessions.findById(id).orElseThrow(() -> notFound(id));
        }));
        return end(session, currentSessionId);
    }

    /**
     * Ends every session of an account, for example after it was disabled, locked or given a new password.
     *
     * @param accountId the account
     */
    public void revokeAll(long accountId)
    {
        revokeOthers(accountId, null);
    }

    /**
     * Ends every session of an account except one, for example the request's own after a password change.
     *
     * @param accountId the account
     * @param keep Spring Session's ID of the session to keep, or {@code null} to end them all
     */
    public void revokeOthers(long accountId, @Nullable String keep)
    {
        terminator.terminateAllOf(accountId, keep);
        transactions.executeWithoutResult(status -> sessions.deleteAllInBatch(sessions.findByAccountId(accountId)
                .stream().filter(session -> !session.getSessionId().equals(keep)).toList()));
    }

    private boolean end(ConsoleSession session, @Nullable String currentSessionId)
    {
        boolean current = session.getSessionId().equals(currentSessionId);
        if (!current) {
            terminator.terminate(session.getSessionId());
        }
        forget(session.getSessionId());
        return current;
    }

    private void requireAdministrator(long actorId)
    {
        if (!accounts.findById(actorId).map(UserAccount::isSystemAccount).orElse(false)) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " may not manage sessions");
        }
    }

    private static GrantForgeException notFound(long id)
    {
        return new GrantForgeException(CommonErrorCode.NOT_FOUND, "no session " + id);
    }

    private static ActiveSession view(ConsoleSession session, String username, @Nullable String displayName,
            @Nullable String currentSessionId)
    {
        return new ActiveSession(session.requireId(), session.getAccountId(), username, displayName,
                session.getClientIp(), session.getUserAgent(), session.getSignedInAt(), session.getLastSeenAt(),
                session.getSessionId().equals(currentSessionId));
    }
}
