// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import static java.util.Objects.requireNonNull;

/** Enforces {@link RequirePermission} on Spring MVC handlers before they run. */
public final class RequirePermissionInterceptor
        implements HandlerInterceptor
{
    private final GrantForge grantForge;

    /**
     * Creates the interceptor.
     *
     * @param grantForge checks the current user
     */
    public RequirePermissionInterceptor(GrantForge grantForge)
    {
        this.grantForge = requireNonNull(grantForge, "grantForge");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        if (handler instanceof HandlerMethod method) {
            RequirePermission required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequirePermission.class);
            if (required == null) {
                required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequirePermission.class);
            }
            if (required != null) {
                grantForge.require(required.value());
            }
        }
        return true;
    }
}
