// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ApiEndpoint;

import static java.util.Objects.requireNonNull;

/**
 * An endpoint as the server's controllers declare it.
 *
 * @param httpMethod the method, such as {@code GET}
 * @param pathPattern the path pattern, such as {@code /api/v1/users/{id}}
 * @param declaration the handler and who may call it
 */
public record DeclaredEndpoint(String httpMethod, String pathPattern, ApiEndpoint.Declaration declaration)
{
    /** Checks that every value is present. */
    public DeclaredEndpoint
    {
        requireNonNull(httpMethod, "httpMethod");
        requireNonNull(pathPattern, "pathPattern");
        requireNonNull(declaration, "declaration");
    }

    /**
     * Returns the route, which identifies the endpoint.
     *
     * @return {@code METHOD path}
     */
    public String route()
    {
        return httpMethod + " " + pathPattern;
    }
}
