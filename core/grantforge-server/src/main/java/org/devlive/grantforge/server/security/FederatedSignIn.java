// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.AuthenticationService;
import org.devlive.grantforge.identity.application.DirectoryUser;
import org.devlive.grantforge.identity.application.IdentityErrorCode;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.devlive.grantforge.identity.application.SignedInAccount;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static java.util.Objects.requireNonNull;

/**
 * Finishes a sign-in with an identity provider (D-72). Spring Security checked the ID token (signature, issuer,
 * audience, nonce); this maps it to the account of the user, signs that account in as a password would, and sends the
 * browser on: to the application whose authorization started it, into the console, or to the second step of an account
 * with two-step sign-in. A refusal sends the browser to the sign-in page with its code.
 */
@Component
public class FederatedSignIn
        implements AuthenticationSuccessHandler, AuthenticationFailureHandler
{
    /** Session attribute with where to go after the sign-in, as {@link FederatedController} found it. */
    static final String TARGET = FederatedSignIn.class.getName() + ".TARGET";

    private static final Logger LOG = LoggerFactory.getLogger(FederatedSignIn.class);

    private final AuthenticationService authentication;
    private final FederatedClients clients;
    private final SignInSessions signIns;
    private final SecurityContextRepository contexts;
    private final Clock clock;

    /**
     * Creates the handler.
     *
     * @param authentication signs accounts in
     * @param clients the providers' claim names
     * @param signIns starts the sessions
     * @param contexts forgets the provider's authentication
     * @param clock source of the current time
     */
    public FederatedSignIn(AuthenticationService authentication, FederatedClients clients, SignInSessions signIns, SecurityContextRepository contexts,
            Clock clock)
    {
        this.authentication = requireNonNull(authentication, "authentication");
        this.clients = requireNonNull(clients, "clients");
        this.signIns = requireNonNull(signIns, "signIns");
        this.contexts = requireNonNull(contexts, "contexts");
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication signedIn) throws IOException
    {
        // The provider's authentication is not a console session: forget it, whatever happens next.
        SecurityContextHolder.clearContext();
        contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        HttpSession session = request.getSession();
        String target = session.getAttribute(TARGET) instanceof String stored ? stored : FederatedController.CONSOLE;
        session.removeAttribute(TARGET);
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) signedIn;
            String code = token.getAuthorizedClientRegistrationId();
            OidcUser oidc = (OidcUser) token.getPrincipal();
            OidcSettings settings = clients.settings(code).orElseThrow(() -> failed("the identity source was disabled"));
            String username = claim(oidc, settings.usernameClaim());
            if (username == null) {
                throw failed("the ID token has no " + settings.usernameClaim());
            }
            SignedInAccount account = authentication.signInFederated(code,
                    new DirectoryUser(requireNonNull(oidc.getSubject(), "sub"), username, claim(oidc, settings.displayNameClaim()),
                            claim(oidc, settings.emailClaim())));
            if (account.secondFactorRequired()) {
                PendingSignIn.start(session, account.accountId(), account.tenantId(), clock.instant());
                response.sendRedirect(FederatedController.secondStep(target));
                return;
            }
            signIns.start(account, request, response, false);
            response.sendRedirect(target);
        }
        catch (GrantForgeException refused) {
            LOG.info("Federated sign-in refused: {}", refused.getMessage());
            response.sendRedirect(refusal(refused.getErrorCode()));
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException
    {
        LOG.info("Federated sign-in failed: {}", failed.getMessage());
        response.sendRedirect(refusal(IdentityErrorCode.FEDERATED_SIGN_IN_FAILED));
    }

    /**
     * Returns where a refused sign-in goes: the sign-in page, told the refusal's code.
     *
     * @param code the refusal
     * @return the console URL
     */
    static String refusal(ErrorCode code)
    {
        return "/#/auth/login?federatedError=" + URLEncoder.encode(code.code(), StandardCharsets.UTF_8);
    }

    private static @Nullable String claim(OidcUser user, String name)
    {
        Object value = user.getClaims().get(name);
        String text = value == null ? null : value.toString().strip();
        return text == null || text.isEmpty() ? null : text;
    }

    private static GrantForgeException failed(String reason)
    {
        return new GrantForgeException(IdentityErrorCode.FEDERATED_SIGN_IN_FAILED, "federated sign-in failed: " + reason, reason);
    }
}
