// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceTreeImportLimitTest
{
    @Test
    void rejectsOversizedKnownAndUnknownLengthButAllowsTheExactLimit() throws Exception
    {
        var filter = new ResourceTreeImportLimit(4);
        for (boolean unknown : new boolean[]{false, true}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/applications/42/resource-tree/import") {
                @Override public long getContentLengthLong()
                {
                    return unknown ? -1 : super.getContentLengthLong();
                }
            };
            request.setContent("12345".getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(413);
            assertThat(chain.getRequest()).isNull();
        }
        var request = new MockHttpServletRequest("POST", "/api/v1/applications/42/resource-tree/import");
        request.setContent("1234".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(requireNonNull(chain.getRequest()).getInputStream().readAllBytes()).isEqualTo("1234".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validatesConfigurationAndLeavesOtherRequestsAlone() throws Exception
    {
        assertThatThrownBy(() -> new ResourceTreeImportLimit(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResourceTreeImportLimit(67108865)).isInstanceOf(IllegalArgumentException.class);
        var request = new MockHttpServletRequest("POST", "/api/v1/other");
        request.setContent("12345".getBytes(StandardCharsets.UTF_8));
        var chain = new MockFilterChain();
        new ResourceTreeImportLimit(4).doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
