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

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role assignments through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:role-assignments",
        "grantforge.setup.token=" + RoleAssignmentControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class RoleAssignmentControllerTest
{
    static final String TOKEN = "role-assignments-token-012345";
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

    private ResultActions send(Cookie session, String method, String path, String body) throws Exception
    {
        var request = "PUT".equals(method) ? put(path) : post(path);
        return mvc.perform(request.with(csrf()).cookie(session).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String id(ResultActions result, String path) throws Exception
    {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    @Test
    void rolesReachAccountsDirectlyAndThroughTheirGroups() throws Exception
    {
        Cookie root = login();
        String rootId = id(mvc.perform(get("/api/v1/users").param("q", "root").cookie(root)), "$.items[0].id");
        // The system account has its system roles from the start.
        String roles = mvc.perform(get("/api/v1/users/" + rootId + "/roles").cookie(root)).andReturn().getResponse()
                .getContentAsString();
        assertThat(JsonPath.<List<String>>read(roles, "$[*].role.code")).containsExactly("platform-admin", "tenant-admin");

        String alice = id(send(root, "POST", "/api/v1/users", """
                {"username": "alice", "password": "a long enough password", "profile": {"displayName": "Alice",
                 "otherUnitIds": [], "positionIds": []}}
                """), "$.user.id");
        String dev = id(send(root, "POST", "/api/v1/groups", "{\"code\": \"dev\", \"name\": \"Developers\"}"), "$.id");
        send(root, "POST", "/api/v1/groups/" + dev + "/members", "{\"accountIds\": [\"%s\"]}".formatted(alice));
        String auditors = id(send(root, "POST", "/api/v1/roles", "{\"code\": \"auditors\", \"name\": \"审计员\"}"), "$.id");

        String direct = id(send(root, "POST", "/api/v1/roles/" + auditors + "/assignments", """
                {"subjectType": "USER", "subjectId": "%s", "validTo": "2099-01-01T00:00:00Z"}
                """.formatted(alice)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.subjectName").value("Alice"))
                .andExpect(jsonPath("$.valid").value(true)), "$.id");
        send(root, "POST", "/api/v1/roles/" + auditors + "/assignments", "{\"subjectType\": \"GROUP\", \"subjectId\": \"%s\"}"
                .formatted(dev)).andExpect(status().isCreated());
        send(root, "POST", "/api/v1/roles/" + auditors + "/assignments", "{\"subjectType\": \"GROUP\", \"subjectId\": \"%s\"}"
                .formatted(dev)).andExpect(jsonPath("$.code").value("GF-AUTHZ-032"));
        send(root, "POST", "/api/v1/roles/" + auditors + "/assignments", """
                {"subjectType": "USER", "subjectId": "%s", "validFrom": "2030-01-01T00:00:00Z", "validTo": "2029-01-01T00:00:00Z"}
                """.formatted(rootId)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("有效期的结束时间必须晚于开始时间。"));
        send(root, "POST", "/api/v1/roles/" + auditors + "/assignments", "{\"subjectId\": \"1\"}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/roles/" + auditors + "/assignments").cookie(root)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/users/" + alice + "/roles").cookie(root))
                .andExpect(jsonPath("$[0].role.code").value("auditors"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].sources.length()").value(2));
        send(root, "PUT", "/api/v1/role-assignments/" + direct, "{\"validTo\": \"2000-01-01T00:00:00Z\"}")
                .andExpect(jsonPath("$.valid").value(false));
        mvc.perform(delete("/api/v1/role-assignments/" + direct).with(csrf()).cookie(root)).andExpect(status().isNoContent());

        // The system account keeps its system roles.
        String rootAdmin = JsonPath.<List<String>>read(roles, "$[?(@.role.code == 'tenant-admin')].sources[0].id").get(0);
        mvc.perform(delete("/api/v1/role-assignments/" + rootAdmin).with(csrf()).cookie(root))
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-035"));
        // Deleting the group removes its assignment.
        mvc.perform(delete("/api/v1/groups/" + dev).with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/roles/" + auditors + "/assignments").cookie(root)).andExpect(jsonPath("$").isEmpty());
    }
}
