// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:me-controller")
@AutoConfigureMockMvc
class MeControllerTest
{
    @Autowired
    private MockMvc mvc;

    @Test
    void sessionsOfVanishedAccountsAreTreatedAsSignedOut() throws Exception
    {
        SessionUser ghost = new SessionUser(404, 1, "ghost");

        mvc.perform(get("/api/v1/me").with(authentication(
                        UsernamePasswordAuthenticationToken.authenticated(ghost, null, List.of()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GF-COMMON-401"));
    }
}
