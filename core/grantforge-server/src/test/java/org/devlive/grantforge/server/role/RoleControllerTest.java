// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

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

/** Roles through the assembled server: each tenant has its own, system roles included. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:role-flow",
        "grantforge.setup.token=" + RoleControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class RoleControllerTest
{
    static final String TOKEN = "role-flow-token-0123456789abcd";
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
                    {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
            mvc.perform(post("/api/v1/tenants").with(csrf()).cookie(login("root", PASSWORD))
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"code": "acme", "name": "Acme", "adminUsername": "boss", "adminPassword": "%s"}
                            """.formatted(PASSWORD))).andExpect(status().isCreated());
            mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(login("boss", PASSWORD))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s!\"}".formatted(PASSWORD, PASSWORD)))
                    .andExpect(status().isNoContent());
        }
    }

    private Cookie login(String username, String password) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions json(Cookie session, String method, String path, String body) throws Exception
    {
        var request = switch (method) {
            case "PUT" -> put(path);
            default -> post(path);
        };
        return mvc.perform(request.with(csrf()).cookie(session).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void everyTenantHasItsSystemRolesAndManagesItsOwnRoles() throws Exception
    {
        Cookie root = login("root", PASSWORD);
        mvc.perform(get("/api/v1/roles").cookie(root))
                .andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.contains("platform-admin", "tenant-admin")));

        Cookie boss = login("boss", PASSWORD + "!");
        String body = mvc.perform(get("/api/v1/roles").cookie(boss))
                .andExpect(jsonPath("$[0].code").value("tenant-admin"))
                .andExpect(jsonPath("$[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String admin = JsonPath.read(body, "$[0].id");

        String auditors = JsonPath.read(json(boss, "POST", "/api/v1/roles", "{\"code\": \"auditors\", \"name\": \"审计员\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andReturn().getResponse().getContentAsString(), "$.id");
        json(boss, "POST", "/api/v1/roles", "{\"code\": \"auditors\", \"name\": \"Again\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("角色编码“auditors”已被使用。"));
        json(boss, "PUT", "/api/v1/roles/" + auditors, "{\"code\": \"auditors\", \"name\": \"审计\", \"description\": \"只读\"}")
                .andExpect(jsonPath("$.description").value("只读"));
        mvc.perform(post("/api/v1/roles/" + auditors + "/disable").with(csrf()).cookie(boss))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/roles/" + auditors + "/enable").with(csrf()).cookie(boss))
                .andExpect(jsonPath("$.enabled").value(true));
        String helpers = JsonPath.read(json(boss, "POST", "/api/v1/roles/" + admin + "/copy", "{\"code\": \"helpers\", \"name\": \"助手\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CUSTOM"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/roles").param("q", "助手").cookie(boss)).andExpect(jsonPath("$[0].id").value(helpers));
        mvc.perform(get("/api/v1/roles/" + helpers).cookie(boss)).andExpect(jsonPath("$.name").value("助手"));

        mvc.perform(post("/api/v1/roles/" + admin + "/disable").with(csrf()).cookie(boss))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-031"));
        mvc.perform(delete("/api/v1/roles/" + helpers).with(csrf()).cookie(boss)).andExpect(status().isNoContent());
        // Another tenant's roles do not exist for the platform administrator either.
        mvc.perform(get("/api/v1/roles/" + auditors).cookie(root)).andExpect(status().isNotFound());
        json(boss, "POST", "/api/v1/roles", "{}").andExpect(status().isBadRequest());
    }
}
