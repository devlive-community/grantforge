// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends a browser that asks the authorization endpoint without being signed in to the console's sign-in page, which
 * returns it to the request once the user has signed in ({@value #PARAMETER} names the request; the console only follows
 * paths of the authorization endpoint).
 */
public final class ConsoleSignIn
        implements AuthenticationEntryPoint
{
    /** Query parameter of the sign-in page naming the request to return to. */
    public static final String PARAMETER = "authorize";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException reason) throws IOException
    {
        String query = request.getQueryString();
        String back = request.getRequestURI().substring(request.getContextPath().length()) + (query == null ? "" : "?" + query);
        response.sendRedirect(request.getContextPath() + "/#/auth/login?" + PARAMETER + "=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
    }
}
