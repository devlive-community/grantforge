// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationSnapshot;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.common.security.PublicEndpoint;
import org.devlive.grantforge.common.security.RequirePermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PermissionGuardTest
{
    /** Handlers with each kind of declaration, and one without. */
    static final class Handlers
    {
        @PublicEndpoint
        void open()
        {
        }

        @AuthenticatedEndpoint
        void signedIn()
        {
        }

        @RequirePermission("system.user.read")
        void read()
        {
        }

        void undeclared()
        {
        }
    }

    private final AuthorizationEvaluator evaluator = mock(AuthorizationEvaluator.class);
    private final PermissionGuard guard = new PermissionGuard(evaluator);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    private boolean call(String method) throws NoSuchMethodException
    {
        return guard.preHandle(new MockHttpServletRequest(), response,
                new HandlerMethod(new Handlers(), Handlers.class.getDeclaredMethod(method)));
    }

    private static void signIn(Object principal)
    {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of()));
    }

    private static void assertDenied(ThrowingCall call)
    {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(SecurityErrorCode.PERMISSION_DENIED));
    }

    /** A call that may throw. */
    @FunctionalInterface
    interface ThrowingCall
    {
        void run() throws Exception;
    }

    @Test
    void openAndSignedInHandlersAndOtherHandlersPassWithoutWorkingOutPermissions() throws Exception
    {
        assertThat(call("open")).isTrue();
        assertThat(call("signedIn")).isTrue();
        assertThat(guard.preHandle(new MockHttpServletRequest(), response, new Object())).isTrue();
        verifyNoInteractions(evaluator);
    }

    @Test
    void permissionHandlersNeedTheCallersPermissionAndReportItsVersion() throws Exception
    {
        assertDenied(() -> call("read"));
        signIn("not a session user");
        assertDenied(() -> call("read"));

        AuthorizationSnapshot holds = new AuthorizationSnapshot(7, List.of("r"), Set.of(), Set.of("system.user.read"),
                Instant.EPOCH);
        AuthorizationSnapshot lacks = new AuthorizationSnapshot(7, List.of(), Set.of(), Set.of(), Instant.EPOCH);
        when(evaluator.snapshot(7)).thenReturn(holds, lacks);
        signIn(new SessionUser(7, 1, "alice"));
        assertThat(call("read")).isTrue();
        assertThat(response.getHeader(PermissionGuard.VERSION_HEADER))
                .isEqualTo(Long.toString(AuthorizationResponse.versionOf(holds)));

        assertDenied(() -> call("read"));
        assertThat(response.getHeader(PermissionGuard.VERSION_HEADER))
                .isEqualTo(Long.toString(AuthorizationResponse.versionOf(lacks)));
    }

    @Test
    void undeclaredHandlersAreRefused()
    {
        assertDenied(() -> call("undeclared"));
    }
}
