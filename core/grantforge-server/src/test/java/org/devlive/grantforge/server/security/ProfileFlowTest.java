// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profile and password changes of the signed-in user, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profile-flow",
        "grantforge.setup.token=" + ProfileFlowTest.TOKEN,
})
@AutoConfigureMockMvc
class ProfileFlowTest
{
    static final String TOKEN = "profile-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";
    private static final String NEW_PASSWORD = "an even longer new password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder encoder;

    private long tenant;

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
        tenant = requireNonNull(jdbc.queryForObject("SELECT id FROM gf_tenant", Long.class));
        jdbc.update("DELETE FROM gf_console_session");
        jdbc.update("DELETE FROM GF_SESSION");
        jdbc.update("DELETE FROM gf_password_history");
        jdbc.update("DELETE FROM gf_audit_event");
        jdbc.update("DELETE FROM gf_user_account WHERE system_account = ?", false);
    }

    private long createUser(String username, boolean mustChange)
    {
        UserAccount account = UserAccount.create(username, encoder.encode(PASSWORD), Instant.now());
        if (mustChange) {
            account.requirePasswordChange();
        }
        return TenantContext.callInTenant(tenant, () -> accounts.save(account).requireId());
    }

    private ResultActions login(String username, String password) throws Exception
    {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    private Cookie session(String username, String password) throws Exception
    {
        return requireNonNull(login(username, password).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions changePassword(Cookie session, String current, String next) throws Exception
    {
        return mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(session)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, next)));
    }

    @Test
    void usersEditTheirProfile() throws Exception
    {
        createUser("carol", false);
        Cookie carol = session("carol", PASSWORD);

        mvc.perform(put("/api/v1/me").with(csrf()).cookie(carol).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \" 卡罗尔 \", \"email\": \"carol@example.org\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("卡罗尔"))
                .andExpect(jsonPath("$.email").value("carol@example.org"));
        mvc.perform(get("/api/v1/me").cookie(carol)).andExpect(jsonPath("$.displayName").value("卡罗尔"));

        mvc.perform(put("/api/v1/me").with(csrf()).cookie(carol).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not an address\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-COMMON-400"));
        mvc.perform(put("/api/v1/me").with(csrf()).cookie(carol).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"%s\"}".formatted("x".repeat(129))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/me").with(csrf()).cookie(carol).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    void changingThePasswordEndsTheOtherSessions() throws Exception
    {
        createUser("dave", false);
        Cookie laptop = session("dave", PASSWORD);
        Cookie phone = session("dave", PASSWORD);

        changePassword(laptop, "wrong password", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-015"))
                .andExpect(jsonPath("$.detail").value("当前密码不正确。"));
        changePassword(laptop, PASSWORD, "short").andExpect(jsonPath("$.code").value("GF-IDENTITY-010"));
        changePassword(laptop, PASSWORD, "").andExpect(status().isBadRequest());

        changePassword(laptop, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").cookie(laptop)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").cookie(phone)).andExpect(status().isUnauthorized());
        login("dave", PASSWORD).andExpect(status().isUnauthorized());
        login("dave", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aDemandedPasswordChangeComesFirst() throws Exception
    {
        createUser("erin", true);
        Cookie erin = session("erin", PASSWORD);

        mvc.perform(get("/api/v1/me").cookie(erin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mvc.perform(get("/api/v1/me/authorization").cookie(erin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/sessions").cookie(erin).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-016"))
                .andExpect(jsonPath("$.detail").value("请先修改密码后再继续操作。"));

        changePassword(erin, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").cookie(erin)).andExpect(jsonPath("$.passwordChangeRequired").value(false));
        mvc.perform(get("/api/v1/me/sessions").cookie(erin)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(erin)).andExpect(status().isNoContent());
    }

    @Test
    void usersSeeTheirSignInsRefusalsAndSignOuts() throws Exception
    {
        createUser("frank", false);
        login("frank", "wrong password").andExpect(status().isUnauthorized());
        Cookie frank = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.USER_AGENT, "Firefox")
                        .header("X-Request-Id", "req-frank")
                        .content("{\"username\": \"frank\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        Cookie other = session("frank", PASSWORD);
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(other)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me/login-history").param("size", "10").cookie(frank))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[0].action").value("LOGOUT"))
                .andExpect(jsonPath("$.items[2].action").value("LOGIN_SUCCEEDED"))
                .andExpect(jsonPath("$.items[2].userAgent").value("Firefox"))
                .andExpect(jsonPath("$.items[2].clientIp").value("127.0.0.1"))
                .andExpect(jsonPath("$.items[3].action").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.items[3].outcome").value("FAILURE"))
                .andExpect(jsonPath("$.items[3].reason").value("GF-IDENTITY-020"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM gf_audit_event WHERE request_id = 'req-frank' AND action = 'LOGIN_SUCCEEDED'",
                Integer.class)).isOne();

        // Ending a session is audited too, but it is not part of the login history.
        mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(frank).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/login-history").cookie(frank)).andExpect(jsonPath("$.total").value(4));
        assertThat(jdbc.queryForList(
                "SELECT action FROM gf_audit_event WHERE action IN ('PASSWORD_CHANGED', 'SESSION_REVOKED')", String.class))
                .containsExactlyInAnyOrder("PASSWORD_CHANGED");
    }
}
