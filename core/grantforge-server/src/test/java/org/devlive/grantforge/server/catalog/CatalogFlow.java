// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sets up the platform and a tenant for the catalog API tests and signs in. */
final class CatalogFlow
{
    static final String PASSWORD = "a long enough password";

    private final MockMvc mvc;

    CatalogFlow(MockMvc mvc, JdbcTemplate jdbc, String token) throws Exception
    {
        this.mvc = mvc;
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                    """.formatted(token, PASSWORD))).andExpect(status().isOk());
            Cookie root = login("root");
            mvc.perform(post("/api/v1/tenants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON).content("""
                    {"code": "acme", "name": "Acme", "adminUsername": "boss", "adminPassword": "%s"}
                    """.formatted(PASSWORD))).andExpect(status().isCreated());
            Cookie boss = login("boss", PASSWORD);
            mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(boss).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(PASSWORD, PASSWORD + "!")))
                    .andExpect(status().isNoContent());
        }
    }

    /** Signs in {@code root}, or {@code boss} after the first password change. */
    Cookie login(String username) throws Exception
    {
        return login(username, "boss".equals(username) ? PASSWORD + "!" : PASSWORD);
    }

    private Cookie login(String username, String password) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    String consoleId(Cookie session) throws Exception
    {
        String body = mvc.perform(get("/api/v1/applications").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    ResultActions createResource(Cookie session, String application, String json) throws Exception
    {
        return mvc.perform(post("/api/v1/applications/" + application + "/resources").with(csrf()).cookie(session)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static String idOf(ResultActions result) throws Exception
    {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
