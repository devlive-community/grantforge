// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

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

import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Data policies of roles through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:datapolicies",
        "grantforge.setup.token=" + DataPolicyControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class DataPolicyControllerTest
{
    static final String TOKEN = "data-token-0123456789abcdef";
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

    @Test
    void managesTheDataPoliciesOfARole() throws Exception
    {
        Cookie root = login();
        mvc.perform(get("/api/v1/data-entities").cookie(root))
                .andExpect(jsonPath("$.entities[?(@.code == 'user')].scopes[*]", hasItem("SELF")))
                .andExpect(jsonPath("$.entities[?(@.code == 'user')].fields[?(@.code == 'status')].choices[*]", hasItem("DISABLED")))
                .andExpect(jsonPath("$.entities[?(@.code == 'user')].fields[?(@.code == 'status')].operators[*]", hasItem("in")))
                .andExpect(jsonPath("$.variables[?(@.key == 'subject.orgUnitIds')].list", hasItem(true)));
        String role = JsonPath.read(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"auditors\", \"name\": \"Auditors\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        String created = mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"entityCode": "user", "action": "READ", "scope": "CONDITION",
                                 "condition": {"and": [{"field": "status", "op": "eq", "value": "ACTIVE"},
                                                       {"field": "username", "op": "ne", "value": {"var": "subject.username"}}]}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleId").value(role))
                .andExpect(jsonPath("$.effect").value("ALLOW"))
                .andExpect(jsonPath("$.condition.and[1].value.var").value("subject.username"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/v1/roles/" + role + "/data-policies").cookie(root)).andExpect(jsonPath("$[0].scope").value("CONDITION"));

        mvc.perform(put("/api/v1/data-policies/" + id).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entityCode\": \"user\", \"action\": \"EXPORT\", \"scope\": \"ALL\", \"effect\": \"DENY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("ALL"))
                .andExpect(jsonPath("$.condition").doesNotExist());
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").content("""
                                {"entityCode": "user", "action": "READ", "scope": "CONDITION",
                                 "condition": {"field": "passwordHash", "op": "eq", "value": "x"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-050"))
                .andExpect(jsonPath("$.errors[?(@.field == 'condition.field')].message").value("条件不能使用字段“passwordHash”。"));
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entityCode\": \"user\", \"action\": \"READ\", \"scope\": \"CUSTOM_ORGS\", \"orgUnitIds\": [\"x\"]}"))
                .andExpect(status().isBadRequest());
        String rootId = JsonPath.read(mvc.perform(get("/api/v1/users?q=root").cookie(root)).andReturn().getResponse()
                .getContentAsString(), "$.items[0].id");
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies/preview").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": \"%s\", \"entityCode\": \"user\", \"action\": \"EXPORT\"}".formatted(rootId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withRole").value(0))
                .andExpect(jsonPath("$.now").value(1));
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies/preview").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\": \"x\", \"entityCode\": \"user\", \"action\": \"READ\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/data-policies/" + id).with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/roles/" + role + "/data-policies").cookie(root)).andExpect(jsonPath("$.length()").value(0));
    }
}
