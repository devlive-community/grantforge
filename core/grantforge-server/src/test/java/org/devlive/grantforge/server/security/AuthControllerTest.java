// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.identity.application.MfaService;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.testsupport.TotpCodes;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Autowired
    private MfaService mfa;

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

    @AfterEach
    void turnTwoStepSignInOff()
    {
        jdbc.update("DELETE FROM gf_mfa_recovery_code");
        jdbc.update("DELETE FROM gf_mfa_factor");
        jdbc.update("UPDATE gf_user_account SET failed_attempts = 0, locked_until = NULL");
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

    @Test
    void accountsWithTwoStepSignInGiveACodeAfterThePassword() throws Exception
    {
        Map<String, Object> admin = jdbc.queryForMap("SELECT id, tenant_id FROM gf_user_account WHERE username_norm = 'admin'");
        long accountId = ((Number) requireNonNull(admin.get("id"))).longValue();
        long tenantId = ((Number) requireNonNull(admin.get("tenant_id"))).longValue();
        String secret = TenantContext.callInTenant(tenantId, () -> mfa.enroll(accountId).secret());
        List<String> recovery = TenantContext.callInTenant(tenantId, () -> mfa.confirm(accountId, TotpCodes.code(secret, Instant.now())));

        // Without a password first, a code signs nobody in.
        mvc.perform(post("/api/v1/auth/mfa").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-104"));

        MvcResult half = login("admin", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-105"))
                .andReturn();
        Cookie waiting = requireNonNull(half.getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        // The waiting session is not signed in.
        mvc.perform(get("/api/v1/me").cookie(waiting)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/mfa").with(csrf()).cookie(waiting).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"000000-bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-100"));
        mvc.perform(post("/api/v1/auth/mfa").with(csrf()).cookie(waiting).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        MvcResult signedIn = mvc.perform(post("/api/v1/auth/mfa").with(csrf()).cookie(waiting).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(TotpCodes.code(secret, Instant.now().plusSeconds(30)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Admin"))
                .andReturn();
        Cookie session = requireNonNull(signedIn.getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        assertThat(session.getValue()).isNotEqualTo(waiting.getValue());
        mvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isOk());
        // The sign-in is complete, so the code cannot complete it again.
        mvc.perform(post("/api/v1/auth/mfa").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(recovery.get(0))))
                .andExpect(status().isUnauthorized());

        // Confirming again for sensitive operations takes a code too.
        mvc.perform(post("/api/v1/auth/step-up").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"wrong\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/step-up").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(recovery.get(1))))
                .andExpect(status().isNoContent());
        String history = mvc.perform(get("/api/v1/me/login-history").cookie(session)).andReturn().getResponse().getContentAsString();
        List<String> actions = JsonPath.read(history, "$.items[*].action");
        assertThat(actions).contains("MFA_STEP_UP", "MFA_ENABLED", "LOGIN_SUCCEEDED");
    }
}
