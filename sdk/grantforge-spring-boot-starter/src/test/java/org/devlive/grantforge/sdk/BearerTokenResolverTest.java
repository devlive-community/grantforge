// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenResolverTest
{
    private final BearerTokenResolver resolver = new BearerTokenResolver();

    @AfterEach
    void clear()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsTheBearerTokenOfTheCurrentRequest()
    {
        assertThat(resolver.currentToken()).isNull();
        assertThat(with("Bearer abc ")).isEqualTo("abc");
        assertThat(with("bearer abc")).isEqualTo("abc");
        assertThat(with("Basic abc")).isNull();
        assertThat(with("Bearer  ")).isNull();
        assertThat(with(null)).isNull();
    }

    private @Nullable String with(@Nullable String authorization)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return resolver.currentToken();
    }
}
