// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.common.security.PublicEndpoint;
import org.devlive.grantforge.common.security.RequirePermission;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads who may call a handler method from its access annotation, or from its controller's when the method has
 * none. Exactly one of {@link PublicEndpoint}, {@link AuthenticatedEndpoint} and {@link RequirePermission} must
 * apply.
 */
final class EndpointDeclarations
{
    private EndpointDeclarations()
    {
    }

    /**
     * Reads a handler's declaration.
     *
     * @param handler the handler method
     * @return the declaration
     * @throws IllegalStateException naming the handler if it has no access annotation or more than one
     */
    static ApiEndpoint.Declaration of(HandlerMethod handler)
    {
        String name = handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
        List<ApiEndpoint.Declaration> found = declared(handler.getMethod(), name);
        if (found.isEmpty()) {
            found = declared(handler.getBeanType(), name);
        }
        if (found.size() != 1) {
            throw new IllegalStateException(name + (found.isEmpty() ? " has no access annotation"
                    : " has more than one access annotation") + " (@PublicEndpoint, @AuthenticatedEndpoint or @RequirePermission)");
        }
        return found.get(0);
    }

    private static List<ApiEndpoint.Declaration> declared(AnnotatedElement element, String name)
    {
        List<ApiEndpoint.Declaration> found = new ArrayList<>();
        if (AnnotatedElementUtils.hasAnnotation(element, PublicEndpoint.class)) {
            found.add(new ApiEndpoint.Declaration(name, EndpointAccess.PUBLIC, null));
        }
        if (AnnotatedElementUtils.hasAnnotation(element, AuthenticatedEndpoint.class)) {
            found.add(new ApiEndpoint.Declaration(name, EndpointAccess.AUTHENTICATED, null));
        }
        RequirePermission permission = AnnotatedElementUtils.findMergedAnnotation(element, RequirePermission.class);
        if (permission != null) {
            found.add(new ApiEndpoint.Declaration(name, EndpointAccess.PERMISSION, permission.value()));
        }
        return found;
    }

    /**
     * Returns the value of the {@code x-permission} extension of an endpoint in the OpenAPI document.
     *
     * @param declaration the declaration
     * @return {@code public}, {@code authenticated} or the permission code
     */
    static String extension(ApiEndpoint.Declaration declaration)
    {
        return switch (declaration.access()) {
            case PUBLIC -> "public";
            case AUTHENTICATED -> "authenticated";
            case PERMISSION -> String.valueOf(declaration.permission());
        };
    }
}
