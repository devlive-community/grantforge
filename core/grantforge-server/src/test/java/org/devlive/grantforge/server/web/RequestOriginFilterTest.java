// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.web;

import org.devlive.grantforge.audit.application.AuditContext;
import org.devlive.grantforge.audit.application.RequestOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestOriginFilterTest
{
    @Test
    void bindsTheOriginForTheRestOfTheRequest() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("User-Agent", "Firefox");
        request.addHeader(RequestIdFilter.HEADER, "req-1");
        AtomicReference<RequestOrigin> seen = new AtomicReference<>();

        new RequestIdFilter().doFilter(request, new MockHttpServletResponse(), (outer, response) ->
                new RequestOriginFilter().doFilter(outer, response, (inner, last) -> seen.set(AuditContext.current())));

        assertThat(seen.get()).isEqualTo(new RequestOrigin("req-1", "10.0.0.1", "Firefox"));
        assertThat(AuditContext.current()).isSameAs(RequestOrigin.NONE);
    }
}
