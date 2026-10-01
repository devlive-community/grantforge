// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in, session restore and sign-out through the assembled server, with sessions in the database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-flow",
        "grantforge.setup.token=" + AuthControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class AuthControllerTest
{
    static final String TOKEN = "auth-flow-token-0123456789";
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
                    {"token": "%s", "username": "Admin", "password": "%s", "displayName": "The Admin"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM GF_SESSION");
    }

    private ResultActions login(String username, String password) throws Exception
    {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    private int sessions()
    {
        return requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM GF_SESSION", Integer.class));
    }

    @Test
    void anonymousRequestsGetALocalizedProblem() throws Exception
    {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GF-COMMON-401"))
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));
    }

    @Test
    void stateChangingRequestsNeedTheCsrfToken() throws Exception
    {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GF-SECURITY-001"))
                .andExpect(jsonPath("$.detail").value("页面已过期，请刷新后重试。"));
    }

    @Test
    void wrongCredentialsAndInvalidInputAreRejected() throws Exception
    {
        login("admin", "wrong password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-020"))
                .andExpect(jsonPath("$.detail").value("用户名或密码错误。"));
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        assertThat(sessions()).isZero();
    }

    @Test
    void sessionLifecycle() throws Exception
    {
        MvcResult signedIn = login("ADMIN", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Admin"))
                .andExpect(jsonPath("$.displayName").value("The Admin"))
                .andExpect(jsonPath("$.tenantCode").value("default"))
                .andExpect(jsonPath("$.systemAccount").value(true))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false))
                .andReturn();
        Cookie session = requireNonNull(signedIn.getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(sessions()).isOne();

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantName").value("Default"))
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty());

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
        assertThat(sessions()).isZero();
        mvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isUnauthorized());

        // Signing out without a session is harmless.
        mvc.perform(post("/api/v1/auth/logout").with(csrf())).andExpect(status().isNoContent());
    }
}
