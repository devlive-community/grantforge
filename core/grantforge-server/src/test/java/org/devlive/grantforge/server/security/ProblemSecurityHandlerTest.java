// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProblemSecurityHandlerTest
{
    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final ProblemSecurityHandler handler = new ProblemSecurityHandler(resolver);

    private ErrorCode resolved()
    {
        ArgumentCaptor<Exception> error = ArgumentCaptor.forClass(Exception.class);
        verify(resolver).resolveException(any(), any(), isNull(), error.capture());
        return ((GrantForgeException) error.getValue()).getErrorCode();
    }

    @Test
    void missingAuthenticationIs401()
    {
        handler.commence(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new InsufficientAuthenticationException("no session"));

        assertThat(resolved()).isEqualTo(CommonErrorCode.UNAUTHENTICATED);
    }

    @Test
    void csrfRejectionsAreTellable()
    {
        handler.handle(new MockHttpServletRequest(), new MockHttpServletResponse(), new MissingCsrfTokenException(null));

        assertThat(resolved()).isEqualTo(SecurityErrorCode.CSRF_REJECTED);
    }

    @Test
    void otherDenialsAre403()
    {
        handler.handle(new MockHttpServletRequest(), new MockHttpServletResponse(), new AccessDeniedException("no"));

        assertThat(resolved()).isEqualTo(CommonErrorCode.FORBIDDEN);
    }
}
