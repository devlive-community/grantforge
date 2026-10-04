// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.testsupport.TotpCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Setting up, using and turning off two-step sign-in through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mfa-flow",
        "grantforge.setup.token=" + MfaControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class MfaControllerTest
{
    static final String TOKEN = "mfa-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie session;

    @BeforeEach
    void signIn() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM gf_mfa_recovery_code");
        jdbc.update("DELETE FROM gf_mfa_factor");
        session = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions send(String path, String code) throws Exception
    {
        return mvc.perform(post("/api/v1/me/mfa" + path).with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\"}".formatted(code)));
    }

    @Test
    void setsUpAnAuthenticatorAndTurnsItOff() throws Exception
    {
        mvc.perform(get("/api/v1/me/mfa").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(0));
        String enrollment = mvc.perform(post("/api/v1/me/mfa/enroll").with(csrf()).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/GrantForge%3Aadmin?secret=")))
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(enrollment, "$.secret");

        send("/confirm", "000000-bad").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GF-IDENTITY-100"));
        String confirmed = send("/confirm", TotpCodes.code(secret, Instant.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        List<String> codes = JsonPath.read(confirmed, "$.codes");
        mvc.perform(get("/api/v1/me/mfa").cookie(session)).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(post("/api/v1/me/mfa/enroll").with(csrf()).cookie(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-101"));

        String renewed = send("/recovery-codes", codes.get(0)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> fresh = JsonPath.read(renewed, "$.codes");
        assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(codes);
        send("/disable", codes.get(1)).andExpect(status().isBadRequest());
        send("/disable", fresh.get(0)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/mfa").cookie(session)).andExpect(jsonPath("$.enabled").value(false));
        send("/disable", fresh.get(1)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GF-IDENTITY-103"));
    }

    @Test
    void needsASession() throws Exception
    {
        mvc.perform(get("/api/v1/me/mfa")).andExpect(status().isUnauthorized());
    }
}
