// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Account administration through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-flow",
        "grantforge.setup.token=" + UserControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class UserControllerTest
{
    static final String TOKEN = "user-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";
    private static final String NEW_PASSWORD = "a completely different secret";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie admin;
    private String hq;

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
        jdbc.update("DELETE FROM gf_org_member");
        jdbc.update("DELETE FROM gf_user_account WHERE system_account = ?", false);
        jdbc.update("DELETE FROM gf_org_unit");
        admin = login("admin", PASSWORD);
        hq = JsonPath.read(mvc.perform(post("/api/v1/org-units").with(csrf()).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"hq\", \"name\": \"总部\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private Cookie login(String username, String password) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions act(String id, String action) throws Exception
    {
        return mvc.perform(post("/api/v1/users/" + id + "/" + action).with(csrf()).cookie(admin)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN"));
    }

    private String createCarol() throws Exception
    {
        String body = mvc.perform(post("/api/v1/users").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "carol", "password": "%s",
                                 "profile": {"displayName": "卡罗尔", "email": "carol@acme.io", "primaryUnitId": "%s"}}
                                """.formatted(PASSWORD, hq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("carol"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.primaryUnitName").value("总部"))
                .andExpect(jsonPath("$.memberships[0].unitId").value(hq))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.user.id");
    }

    @Test
    void administratorsCreateFindAndEditAccounts() throws Exception
    {
        String carol = createCarol();

        mvc.perform(get("/api/v1/users").param("q", "卡罗").param("unitId", hq).param("state", "ACTIVE").cookie(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(carol));
        mvc.perform(get("/api/v1/users").cookie(admin)).andExpect(jsonPath("$.total").value(2));
        mvc.perform(put("/api/v1/users/" + carol).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Carol\", \"email\": \"\"}"))
                .andExpect(jsonPath("$.user.displayName").value("Carol"))
                .andExpect(jsonPath("$.user.email").doesNotExist())
                .andExpect(jsonPath("$.memberships.length()").value(0));
        mvc.perform(get("/api/v1/users/" + carol).cookie(admin)).andExpect(jsonPath("$.user.displayName").value("Carol"));
        mvc.perform(post("/api/v1/users").with(csrf()).cookie(admin).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"CAROL\", \"password\": \"%s\", \"profile\": {}}".formatted(PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("用户名“CAROL”已被使用。"));
        mvc.perform(post("/api/v1/users").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));

        // Ordinary accounts cannot administer accounts.
        Cookie carolSession = login("carol", PASSWORD);
        mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(carolSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users").cookie(carolSession)).andExpect(status().isForbidden());
    }

    @Test
    void disablingLockingAndResettingTakeEffectAtOnce() throws Exception
    {
        String carol = createCarol();
        Cookie carolSession = login("carol", PASSWORD);

        act(carol, "disable").andExpect(jsonPath("$.user.status").value("DISABLED"));
        mvc.perform(get("/api/v1/me").cookie(carolSession)).andExpect(status().isUnauthorized());
        act(carol, "enable").andExpect(jsonPath("$.user.status").value("ACTIVE"));

        carolSession = login("carol", PASSWORD);
        act(carol, "lock").andExpect(jsonPath("$.user.lockedUntil").value("9999-01-01T00:00:00Z"));
        mvc.perform(get("/api/v1/me").cookie(carolSession)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users").param("state", "LOCKED").cookie(admin)).andExpect(jsonPath("$.total").value(1));
        act(carol, "unlock").andExpect(jsonPath("$.user.lockedUntil").doesNotExist());

        mvc.perform(post("/api/v1/users/" + carol + "/password").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"%s\"}".formatted(NEW_PASSWORD)))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));
        login("carol", NEW_PASSWORD);

        mvc.perform(delete("/api/v1/users/" + carol).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/" + carol).cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void systemAccountsAndOneselfAreProtected() throws Exception
    {
        String body = mvc.perform(get("/api/v1/users").param("q", "admin").cookie(admin)).andReturn().getResponse()
                .getContentAsString();
        String self = JsonPath.read(body, "$.items[0].id");

        act(self, "disable").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-050"))
                .andExpect(jsonPath("$.detail").value("系统账号和你自己的账号不能被禁用、锁定或删除。"));
        act(self, "lock").andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/users/" + self).with(csrf()).cookie(admin)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/users/x").with(csrf()).cookie(admin)).andExpect(status().isNotFound());
    }
}
