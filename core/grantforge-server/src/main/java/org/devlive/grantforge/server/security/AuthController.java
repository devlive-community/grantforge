// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.AuthenticationService;
import org.devlive.grantforge.identity.application.ConsoleSessionService;
import org.devlive.grantforge.identity.application.ProfileService;
import org.devlive.grantforge.identity.application.SignedInAccount;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

import static java.util.Objects.requireNonNull;

/** Console sign-in and sign-out. */
@RestController
@RequestMapping("/api/v1/auth")
public final class AuthController
{
    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationService authentication;
    private final ProfileService profiles;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    private final CsrfLogoutHandler csrfLogout;
    private final ConsoleSessionService consoleSessions;
    private final Clock clock;
    private final AuditLog audit;

    /**
     * Creates the controller.
     *
     * @param authentication checks credentials
     * @param profiles reads the signed-in user
     * @param contexts stores the authentication in the session
     * @param sessions renews the session ID and CSRF token at sign-in
     * @param csrfTokens clears the CSRF token at sign-out
     * @param consoleSessions indexes sessions for listing and revoking
     * @param clock source of the current time
     * @param audit records sign-outs
     */
    public AuthController(AuthenticationService authentication, ProfileService profiles,
            SecurityContextRepository contexts, SessionAuthenticationStrategy sessions, CsrfTokenRepository csrfTokens,
            ConsoleSessionService consoleSessions, Clock clock, AuditLog audit)
    {
        this.authentication = requireNonNull(authentication, "authentication");
        this.profiles = requireNonNull(profiles, "profiles");
        this.contexts = requireNonNull(contexts, "contexts");
        this.sessions = requireNonNull(sessions, "sessions");
        this.csrfLogout = new CsrfLogoutHandler(requireNonNull(csrfTokens, "csrfTokens"));
        this.consoleSessions = requireNonNull(consoleSessions, "consoleSessions");
        this.clock = requireNonNull(clock, "clock");
        this.audit = requireNonNull(audit, "audit");
    }

    /**
     * Signs in and starts a session.
     *
     * @param body the credentials
     * @param request the request
     * @param response the response, which receives the new session and CSRF cookies
     * @return the signed-in user
     */
    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response)
    {
        SignedInAccount account = authentication.authenticate(body.username(), body.password());
        SessionUser user = new SessionUser(account.accountId(), account.tenantId(), account.username());
        Authentication signedIn = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        sessions.onAuthentication(signedIn, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(signedIn);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        HttpSession session = request.getSession();
        // Signing in records the first activity; the activity filter takes over from the next request.
        session.setAttribute(SessionActivityFilter.RECORDED_AT, clock.millis());
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

    /**
     * Ends the session; succeeds also without one.
     *
     * @param request the request
     * @param response the response, which clears the session and CSRF cookies
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response)
    {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (current != null && current.getPrincipal() instanceof SessionUser user) {
            if (session != null) {
                TenantContext.runInTenant(user.tenantId(), () -> consoleSessions.forget(session.getId()));
            }
            audit.record(new AuditRecord(AuditAction.LOGOUT, AuditOutcome.SUCCESS, user.tenantId(), user.accountId(),
                    user.username(), null, null));
        }
        new SecurityContextLogoutHandler().logout(request, response, current);
        csrfLogout.logout(request, response, current);
    }
}
