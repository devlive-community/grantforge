// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The open API's filter chain: bearer tokens only, no session, no CSRF. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:open-api-security")
@AutoConfigureMockMvc
class OpenApiSecurityTest
{
    @Autowired
    private MockMvc mvc;

    @Test
    void refusesCallsWithoutATokenAsProblemsAndKeepsNoSession() throws Exception
    {
        mvc.perform(get("/api/v1/open/me/authorization"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        // No CSRF token is asked for: what refuses the call is the missing token.
        mvc.perform(post("/api/v1/open/me/authorization")).andExpect(status().isUnauthorized());
    }
}
