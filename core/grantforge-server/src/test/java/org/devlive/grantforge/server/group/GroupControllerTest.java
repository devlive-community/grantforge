// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.security.SecurityConfiguration;
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

import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** User groups through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-flow",
        "grantforge.setup.token=" + GroupControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class GroupControllerTest
{
    static final String TOKEN = "group-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder encoder;

    private Cookie admin;
    private String erin;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
            long tenant = requireNonNull(jdbc.queryForObject("SELECT id FROM gf_tenant", Long.class));
            TenantContext.runInTenant(tenant, () -> accounts.save(UserAccount.create("erin", encoder.encode(PASSWORD),
                    Instant.now()).withDisplayName("艾琳")));
        }
        jdbc.update("DELETE FROM gf_group_member");
        jdbc.update("DELETE FROM gf_user_group");
        erin = String.valueOf(requireNonNull(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'erin'",
                Long.class)));
        admin = login("admin");
    }

    private Cookie login(String username) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    @Test
    void administratorsManageGroupsAndTheirMembers() throws Exception
    {
        String ops = JsonPath.read(mvc.perform(post("/api/v1/groups").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"ops\", \"name\": \"运维组\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members").value(0))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/groups").with(csrf()).cookie(admin).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"OPS\", \"name\": \"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("用户组编码“ops”已被使用。"));

        mvc.perform(post("/api/v1/groups/" + ops + "/members").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountIds\": [\"%s\", \"%s\"]}".formatted(erin, erin)))
                .andExpect(jsonPath("$.changed").value(1));
        mvc.perform(get("/api/v1/groups/" + ops + "/members").param("q", "艾").cookie(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].accountId").value(erin))
                .andExpect(jsonPath("$.items[0].displayName").value("艾琳"));
        mvc.perform(get("/api/v1/groups").param("q", "运维").cookie(admin))
                .andExpect(jsonPath("$.items[0].members").value(1));
        mvc.perform(put("/api/v1/groups/" + ops).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"ops\", \"name\": \"Ops\", \"description\": \"值班\"}"))
                .andExpect(jsonPath("$.description").value("值班"))
                .andExpect(jsonPath("$.members").value(1));
        mvc.perform(post("/api/v1/groups/" + ops + "/members/remove").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountIds\": [\"%s\"]}".formatted(erin)))
                .andExpect(jsonPath("$.changed").value(1));
        mvc.perform(post("/api/v1/groups/" + ops + "/members").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountIds\": []}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/groups/" + ops + "/members").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountIds\": [\"nope\"]}"))
                .andExpect(status().isNotFound());

        // Ordinary accounts cannot manage groups.
        mvc.perform(get("/api/v1/groups").cookie(login("erin"))).andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/groups/" + ops).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/" + ops + "/members").cookie(admin)).andExpect(status().isNotFound());
    }
}
