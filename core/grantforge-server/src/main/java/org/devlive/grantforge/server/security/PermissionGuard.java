// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationSnapshot;
import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.server.catalog.EndpointDeclarations;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Lets an API call through only if the caller may make it: {@code @PublicEndpoint} handlers are open,
 * {@code @AuthenticatedEndpoint} handlers need a session (which the filter chain already demands), and
 * {@code @RequirePermission} handlers need the permission among the caller's current permissions. A handler with
 * no declaration is refused. Permissions are worked out afresh for every call, so a changed grant, assignment or
 * catalog entry applies to the very next call.
 */
@Configuration(proxyBeanMethods = false)
public final class PermissionGuard
        implements WebMvcConfigurer, HandlerInterceptor
{
    private final AuthorizationEvaluator evaluator;
    private final Map<HandlerMethod, ApiEndpoint.Declaration> declarations = new ConcurrentHashMap<>();

    /**
     * Creates the guard.
     *
     * @param evaluator works out the caller's permissions
     */
    public PermissionGuard(AuthorizationEvaluator evaluator)
    {
        this.evaluator = requireNonNull(evaluator, "evaluator");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        ApiEndpoint.Declaration declaration = declarationOf(method);
        if (declaration.access() != EndpointAccess.PERMISSION) {
            return true;
        }
        String permission = String.valueOf(declaration.permission());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionUser user)) {
            throw denied(permission, "anonymous");
        }
        AuthorizationSnapshot snapshot = evaluator.snapshot(user.accountId());
        if (!snapshot.holds(permission)) {
            throw denied(permission, "account " + user.accountId());
        }
        return true;
    }

    private ApiEndpoint.Declaration declarationOf(HandlerMethod method)
    {
        try {
            return declarations.computeIfAbsent(method, EndpointDeclarations::of);
        }
        catch (IllegalStateException undeclared) {
            // Start-up refuses undeclared handlers already; refuse the call too rather than leave a gap.
            throw new GrantForgeException(SecurityErrorCode.PERMISSION_DENIED, String.valueOf(undeclared.getMessage()), undeclared);
        }
    }

    private static GrantForgeException denied(String permission, String caller)
    {
        return new GrantForgeException(SecurityErrorCode.PERMISSION_DENIED, caller + " lacks permission " + permission);
    }
}
