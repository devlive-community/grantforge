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

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role inheritance through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:role-inheritance",
        "grantforge.setup.token=" + RoleInheritanceControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class RoleInheritanceControllerTest
{
    static final String TOKEN = "role-inheritance-token-0123456";
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

    private String create(Cookie root, String code) throws Exception
    {
        return JsonPath.read(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\", \"name\": \"%s\"}".formatted(code, code))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions inherit(Cookie root, String role, String... parents) throws Exception
    {
        return mvc.perform(put("/api/v1/roles/" + role + "/parents").with(csrf()).cookie(root)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentIds\": [%s]}".formatted(String.join(", ", Arrays.stream(parents)
                        .map(parent -> "\"" + parent + "\"").toList()))));
    }

    @Test
    void rolesInheritFromOthersWithoutCycles() throws Exception
    {
        Cookie root = login();
        String base = create(root, "base");
        String viewers = create(root, "viewers");

        inherit(root, viewers, base).andExpect(status().isOk())
                .andExpect(jsonPath("$.role.code").value("viewers"))
                .andExpect(jsonPath("$.parents[0].code").value("base"))
                .andExpect(jsonPath("$.ancestors[0].distance").value(1));
        mvc.perform(get("/api/v1/roles/" + base + "/inheritance").cookie(root))
                .andExpect(jsonPath("$.descendants[0].role.code").value("viewers"));
        mvc.perform(get("/api/v1/role-links").cookie(root))
                .andExpect(jsonPath("$[0].roleId").value(viewers))
                .andExpect(jsonPath("$[0].parentId").value(base));
        inherit(root, base, viewers).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-037"))
                .andExpect(jsonPath("$.detail").value("继承“viewers”会形成循环继承。"));
        inherit(root, viewers).andExpect(status().isOk()).andExpect(jsonPath("$.parents").isEmpty());
    }
}
