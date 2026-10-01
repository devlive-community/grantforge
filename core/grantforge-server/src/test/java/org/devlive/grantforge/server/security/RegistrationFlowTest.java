// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Self-registration through the assembled server, with registration switched on. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:registration-flow",
        "grantforge.setup.token=" + RegistrationFlowTest.TOKEN,
        "grantforge.security.registration-enabled=true",
})
@AutoConfigureMockMvc
class RegistrationFlowTest
{
    static final String TOKEN = "registration-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
    }

    @Test
    void visitorsRegisterAndSignInWithTheirNewAccount() throws Exception
    {
        mvc.perform(get("/api/v1/bootstrap")).andExpect(jsonPath("$.registrationEnabled").value(true));
        mvc.perform(post("/api/v1/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"visitor\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("visitor"));
        mvc.perform(post("/api/v1/register").with(csrf()).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"VISITOR\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("用户名“VISITOR”已被使用。"));
        mvc.perform(post("/api/v1/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"no-csrf\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"visitor\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCode").value("default"))
                .andExpect(jsonPath("$.systemAccount").value(false))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }
}
