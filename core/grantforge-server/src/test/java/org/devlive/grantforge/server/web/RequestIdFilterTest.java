// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.web;

import jakarta.servlet.FilterChain;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest
{
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void reusesSafeIncomingIdAndExposesItDuringTheRequest() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123_x.y");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<@Nullable String> seenInMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInMdc.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(seenInMdc.get()).isEqualTo("abc-123_x.y");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123_x.y");
        assertThat(RequestIdFilter.currentId(request)).isEqualTo("abc-123_x.y");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesIdWhenHeaderIsMissing() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void clearsMdcEvenWhenTheChainFails()
    {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("downstream");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing))
                .hasMessage("downstream");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "has space", "line\nbreak", "semi;colon", "été",
            "0123456789012345678901234567890123456789012345678901234567890123456789"})
    void replacesUnsafeIds(@Nullable String incoming)
    {
        assertThat(RequestIdFilter.resolve(incoming)).matches(UUID_PATTERN);
    }

    @Test
    void currentIdIsNullOutsideTheFilter()
    {
        assertThat(RequestIdFilter.currentId(new MockHttpServletRequest())).isNull();
    }
}
