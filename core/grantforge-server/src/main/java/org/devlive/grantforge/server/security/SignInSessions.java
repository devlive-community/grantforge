// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.devlive.grantforge.identity.application.ProfileService;
import org.devlive.grantforge.identity.application.SignedInAccount;
import org.devlive.grantforge.oauth.application.OAuthPrincipals;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;

import static java.util.Objects.requireNonNull;

/**
 * Starts the console session of a signed-in account, whether it signed in with a password, a second factor or an
 * identity provider: a new session ID and CSRF token, the principal in the session and the session in the index.
 */
@Component
public class SignInSessions
{
    private static final Logger LOG = LoggerFactory.getLogger(SignInSessions.class);

    private final ProfileService profiles;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final ConsoleSessionService consoleSessions;
    private final Clock clock;

    /**
     * Creates the helper.
     *
     * @param profiles reads the signed-in user
     * @param contexts stores the authentication in the session
     * @param sessions renews the session ID and CSRF token
     * @param consoleSessions indexes sessions for listing and revoking
     * @param clock source of the current time
     */
    public SignInSessions(ProfileService profiles, SecurityContextRepository contexts, SessionAuthenticationStrategy sessions,
            ConsoleSessionService consoleSessions, Clock clock)
    {
        this.profiles = requireNonNull(profiles, "profiles");
        this.contexts = requireNonNull(contexts, "contexts");
        this.sessions = requireNonNull(sessions, "sessions");
        this.consoleSessions = requireNonNull(consoleSessions, "consoleSessions");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Starts the session.
     *
     * @param account the signed-in account
     * @param request the request
     * @param response the response, which receives the new session and CSRF cookies
     * @param secondFactor whether the user just gave a second factor, which then covers sensitive operations for a while
     * @return the signed-in user
     */
    public MeResponse start(SignedInAccount account, HttpServletRequest request, HttpServletResponse response, boolean secondFactor)
    {
        SessionUser user = new SessionUser(account.accountId(), account.tenantId(), account.username());
        // The password factor records when the user signed in, which ID tokens of applications report as auth_time.
        Authentication signedIn = UsernamePasswordAuthenticationToken.authenticated(user, null,
                OAuthPrincipals.passwordAt(clock.instant()));
        sessions.onAuthentication(signedIn, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(signedIn);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        HttpSession session = request.getSession();
        // Signing in records the first activity; the activity filter takes over from the next request.
        session.setAttribute(SessionActivityFilter.RECORDED_AT, clock.millis());
        if (secondFactor) {
            StepUpGuard.verified(session, clock);
        }
        String sessionId = session.getId();
        LOG.info("Account '{}' signed in", user.username());
        MeResponse me = TenantContext.callInTenant(user.tenantId(), () -> {
                    consoleSessions.start(sessionId, user.accountId(), SessionActivityFilter.clientIp(request),
                            SessionActivityFilter.userAgent(request));
                    return profiles.find(user.accountId());
                })
                .map(MeResponse::from)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.UNAUTHENTICATED, "account vanished"));
        // A demanded or expired password confines the session to changing it.
        PasswordChangeGuard.require(session, me.passwordChangeRequired());
        return me;
    }
}
