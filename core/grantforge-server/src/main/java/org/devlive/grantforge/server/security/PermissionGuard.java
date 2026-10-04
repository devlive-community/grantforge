// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationSnapshot;
import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.secured.FieldRules;
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
 * catalog entry applies to the very next call, and the answer reports the version of the caller's permissions in
 * {@value #VERSION_HEADER} so the console can reload them when they changed.
 */
@Configuration(proxyBeanMethods = false)
public final class PermissionGuard
        implements WebMvcConfigurer, HandlerInterceptor
{
    /** Response header with the version of the caller's permissions, as {@code /me/authorization} reports it. */
    public static final String VERSION_HEADER = "X-Authorization-Version";

    private final AuthorizationEvaluator evaluator;
    private final FieldRules fields;
    private final AuditLog audit;
    private final Map<HandlerMethod, ApiEndpoint.Declaration> declarations = new ConcurrentHashMap<>();

    /**
     * Creates the guard.
     *
     * @param evaluator works out the caller's permissions
     * @param fields works out the caller's restricted fields, which the version covers too
     * @param audit records refused calls, without holding them up
     */
    public PermissionGuard(AuthorizationEvaluator evaluator, FieldRules fields, AuditLog audit)
    {
        this.audit = requireNonNull(audit, "audit");
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.fields = requireNonNull(fields, "fields");
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
        response.setHeader(VERSION_HEADER, Long.toString(AuthorizationResponse.versionOf(snapshot, fields.restricted(user.accountId()))));
        if (!snapshot.holds(permission)) {
            audit.recordLater(new AuditRecord(AuditAction.ACCESS_DENIED, AuditOutcome.FAILURE, user.tenantId(), user.accountId(),
                    user.username(), permission, request.getMethod() + " " + request.getRequestURI()));
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
