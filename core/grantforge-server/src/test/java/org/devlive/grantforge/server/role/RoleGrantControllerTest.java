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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role grants on the console's own pages, buttons and APIs through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:role-grants",
        "grantforge.setup.token=" + RoleGrantControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class RoleGrantControllerTest
{
    static final String TOKEN = "role-grants-token-0123456789ab";
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
        }
    }

    private Cookie login() throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"root\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private static String idOf(String json, String code)
    {
        return JsonPath.<List<String>>read(json, "$[?(@.code == '" + code + "')].id").get(0);
    }

    @Test
    void grantingAButtonImpliesItsPageAndTheApisItNeeds() throws Exception
    {
        Cookie root = login();
        String console = JsonPath.read(mvc.perform(get("/api/v1/applications").cookie(root)).andReturn().getResponse()
                .getContentAsString(), "$[0].id");
        String catalog = mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root)).andReturn().getResponse()
                .getContentAsString();
        String edit = idOf(catalog, "system.user.btn.edit");
        String page = idOf(catalog, "system.user");
        String update = idOf(catalog, "api:system.user.update");
        String role = JsonPath.read(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"editors\", \"name\": \"Editors\"}")).andReturn().getResponse().getContentAsString(), "$.id");
        String change = "{\"applicationId\": \"%s\", \"changes\": [{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"}]}".formatted(console, edit);

        mvc.perform(post("/api/v1/roles/" + role + "/grants/preview").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content(change))
                .andExpect(jsonPath("$.grants.length()").value(1))
                .andExpect(jsonPath("$.states[?(@.resourceId == '%s')].state".formatted(update)).value("IMPLIED"))
                .andExpect(jsonPath("$.states[?(@.resourceId == '%s')].reasons[0].via".formatted(page)).value("ANCESTOR"));
        mvc.perform(get("/api/v1/roles/" + role + "/grants").param("applicationId", console).cookie(root))
                .andExpect(jsonPath("$.grants").isEmpty());
        mvc.perform(put("/api/v1/roles/" + role + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content(change))
                .andExpect(jsonPath("$.grants[0].resourceId").value(edit))
                .andExpect(jsonPath("$.readOnly").value(false));
        mvc.perform(put("/api/v1/roles/" + role + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changes\": []}"))
                .andExpect(status().isBadRequest());

        // The tenant administrator role allows the whole access-control module without grants of its own.
        String roles = mvc.perform(get("/api/v1/roles").cookie(root)).andReturn().getResponse().getContentAsString();
        String admin = idOf(roles, "tenant-admin");
        String matrix = mvc.perform(get("/api/v1/roles/" + admin + "/grants").param("applicationId", console).cookie(root))
                .andExpect(jsonPath("$.readOnly").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(matrix, "$.states[*].resourceId")).contains(edit, page, update)
                .doesNotContain(idOf(catalog, "platform.tenant"));
        mvc.perform(put("/api/v1/roles/" + admin + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content(change)).andExpect(jsonPath("$.code").value("GF-AUTHZ-031"));
    }
}
