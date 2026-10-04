// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.common.security.PublicEndpoint;
import org.devlive.grantforge.identity.application.IdentityErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/**
 * Starts a sign-in with the identity provider of a source (D-72): it notes where the browser goes afterwards and sends it
 * to Spring Security, which redirects to the provider. A source that is unknown, disabled or whose provider does not
 * answer sends the browser back to the sign-in page.
 */
@RestController
public final class FederatedController
{
    /** Where Spring Security starts the authorization request, followed by the source's code. */
    static final String AUTHORIZE = "/api/v1/auth/federated/authorize/";

    /** Where a sign-in goes by default. */
    static final String CONSOLE = "/#/dashboard";

    private final FederatedClients clients;

    /**
     * Creates the controller.
     *
     * @param clients the providers
     */
    public FederatedController(FederatedClients clients)
    {
        this.clients = requireNonNull(clients, "clients");
    }

    /**
     * Sends the browser to the provider of a source.
     *
     * @param code the source's code
     * @param authorize the authorization request of an application that sent the user to sign in, if any
     * @param redirect the console page to open afterwards, if any
     * @param request the request, whose session keeps where to go
     * @param response the response, a redirect
     * @throws IOException if the redirect fails
     */
    @PublicEndpoint
    @GetMapping("/api/v1/auth/federated/{code}")
    public void start(@PathVariable String code, @RequestParam(required = false) @Nullable String authorize,
            @RequestParam(required = false) @Nullable String redirect, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        if (clients.provider(code).isEmpty() || clients.findByRegistrationId(code) == null) {
            response.sendRedirect(FederatedSignIn.refusal(IdentityErrorCode.FEDERATED_SIGN_IN_FAILED));
            return;
        }
        request.getSession().setAttribute(FederatedSignIn.TARGET, target(authorize, redirect));
        response.sendRedirect(AUTHORIZE + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }

    /**
     * Returns where a sign-in goes: an application's authorization request on this server, a console page, or the
     * console's start page. Anything else, such as another site, is ignored.
     *
     * @param authorize the authorization request, if any
     * @param redirect the console page, if any
     * @return the path to send the browser to
     */
    static String target(@Nullable String authorize, @Nullable String redirect)
    {
        if (authorize != null && authorize.startsWith("/oauth2/authorize?")) {
            return authorize;
        }
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
            return "/#" + redirect;
        }
        return CONSOLE;
    }

    /**
     * Returns the sign-in page's second step, which goes on to the same place afterwards.
     *
     * @param target where the sign-in goes
     * @return the console URL
     */
    static String secondStep(String target)
    {
        String step = "/#/auth/login?mfa=1";
        if (target.startsWith("/oauth2/authorize?")) {
            return step + "&authorize=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
        }
        if (target.startsWith("/#/") && !target.equals(CONSOLE)) {
            return step + "&redirect=" + URLEncoder.encode(target.substring(2), StandardCharsets.UTF_8);
        }
        return step;
    }
}
