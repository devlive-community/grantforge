// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.policy;

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

/** Policies through the assembled server, on a service of the test plugin's demo type. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:policies",
        "grantforge.setup.token=" + PolicyControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class PolicyControllerTest
{
    static final String TOKEN = "policies-token-0123456789abcdef";
    private static final String PASSWORD = "a long enough password";
    private static final String POLICY = """
            {"name": "%s", "labels": ["pii"], %s
             "document": {"resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false},
                                        "table": {"values": ["*"], "excludes": false, "recursive": false}},
                          "allow": [{"users": ["%s"], "groups": ["public"], "roles": ["tenant-admin"], "accessTypes": ["select"]}],
                          "validity": [{"from": "2026-01-01T00:00:00Z"}]}}
            """;

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
    void managesThePoliciesOfAService() throws Exception
    {
        Cookie root = login();
        String service = JsonPath.read(mvc.perform(post("/api/v1/services").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceType\": \"demo\", \"name\": \"dw\", \"label\": \"DW\", \"values\": {\"url\": \"demo://dw\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/service-types").cookie(root))
                .andExpect(jsonPath("$[0].policyTypes[0]").value("ACCESS"))
                .andExpect(jsonPath("$[0].accessTypes[1].name").value("update"));

        String created = mvc.perform(post("/api/v1/services/" + service + "/policies").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content(POLICY.formatted("sales", "", "ROOT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceId").value(service))
                .andExpect(jsonPath("$.type").value("ACCESS"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.document.resources.table.values[0]").value("*"))
                .andExpect(jsonPath("$.document.validity[0].from").value("2026-01-01T00:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        Integer version = JsonPath.read(created, "$.version");

        mvc.perform(get("/api/v1/services/" + service + "/policies").cookie(root)).andExpect(jsonPath("$[0].name").value("sales"));
        mvc.perform(get("/api/v1/services/" + service + "/policies?type=ROW_FILTER").cookie(root)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/policies/" + id).cookie(root)).andExpect(jsonPath("$.labels[0]").value("pii"));

        mvc.perform(put("/api/v1/policies/" + id).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content(POLICY.formatted("sales readers", "\"priority\": \"OVERRIDE\", \"version\": " + version + ",", "root")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("OVERRIDE"));
        mvc.perform(put("/api/v1/policies/" + id).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content(POLICY.formatted("sales readers", "\"version\": " + version + ",", "root")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/services/" + service + "/policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").content(POLICY.formatted("other", "", "nobody")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-SERVICE-006"))
                .andExpect(jsonPath("$.errors[?(@.field == 'allow[0].users')].message").value("不存在：nobody"));
        mvc.perform(post("/api/v1/services/" + service + "/policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"bare\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/policy-subjects?kind=USER&text=RO").cookie(root)).andExpect(jsonPath("$[0]").value("root"));
        mvc.perform(get("/api/v1/policy-subjects?kind=ROLE&text=admin").cookie(root))
                .andExpect(jsonPath("$", hasItem("platform-admin")));
        mvc.perform(get("/api/v1/policy-subjects?kind=GROUP").cookie(root)).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(delete("/api/v1/policies/" + id).with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/policies/" + id).cookie(root)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/services/" + service).with(csrf()).cookie(root)).andExpect(status().isNoContent());
    }
}
