// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Separation of duties through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sod",
        "grantforge.setup.token=" + SodControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class SodControllerTest
{
    static final String TOKEN = "sod-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void signIn() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM gf_sod_constraint_role");
        jdbc.update("DELETE FROM gf_sod_constraint");
        admin = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception
    {
        return mvc.perform(request.with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .content(body));
    }

    private String id(ResultActions result) throws Exception
    {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String role(String code, String name) throws Exception
    {
        return id(send(post("/api/v1/roles"), "{\"code\": \"%s\", \"name\": \"%s\"}".formatted(code, name)).andExpect(status().isCreated()));
    }

    @Test
    void refusesAssignmentsAgainstEnforcedConstraintsAndReportsTheOthers() throws Exception
    {
        String suffix = Long.toString(System.nanoTime() % 100_000);
        String payer = role("payer-" + suffix, "出纳" + suffix);
        String approver = role("approver-" + suffix, "审批" + suffix);
        String auditor = role("auditor-" + suffix, "审计" + suffix);
        String user = JsonPath.read(send(post("/api/v1/users"), """
                {"username": "erin%s", "password": "%s", "profile": {"displayName": "Erin"}}
                """.formatted(suffix, PASSWORD)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.user.id");

        String payments = id(send(post("/api/v1/sod-constraints"), """
                {"code": "payments-%s", "name": "资金岗位分离", "roleIds": ["%s", "%s"]}
                """.formatted(suffix, payer, approver))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("ENFORCE"))
                .andExpect(jsonPath("$.maxRoles").value(1))
                .andExpect(jsonPath("$.roles.length()").value(2)));
        send(post("/api/v1/sod-constraints"), """
                {"code": "audits-%s", "name": "审计独立", "mode": "REPORT", "roleIds": ["%s", "%s"]}
                """.formatted(suffix, payer, auditor)).andExpect(status().isCreated());

        String assignment = "{\"subjectType\": \"USER\", \"subjectId\": \"%s\"}".formatted(user);
        send(post("/api/v1/roles/" + payer + "/assignments"), assignment).andExpect(status().isCreated());
        send(post("/api/v1/roles/" + approver + "/assignments"), assignment)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-072"))
                .andExpect(jsonPath("$.detail").value("此操作会使 Erin 同时拥有角色 出纳%1$s, 审批%1$s，违反职责分离约束“资金岗位分离”。".formatted(suffix)));
        send(post("/api/v1/roles/" + auditor + "/assignments"), assignment).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/sod-conflicts").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].constraintName").value("审计独立"))
                .andExpect(jsonPath("$[0].mode").value("REPORT"))
                .andExpect(jsonPath("$[0].accountName").value("Erin"))
                .andExpect(jsonPath("$[0].username").value("erin" + suffix))
                .andExpect(jsonPath("$[0].roles.length()").value(2));

        send(put("/api/v1/sod-constraints/" + payments), """
                {"name": "资金", "roleIds": ["%s", "%s"], "enabled": false}
                """.formatted(payer, approver)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        // A disabled constraint refuses nothing.
        send(post("/api/v1/roles/" + approver + "/assignments"), assignment).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/sod-constraints").cookie(admin)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(delete("/api/v1/sod-constraints/" + payments).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/sod-constraints").cookie(admin)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void administratorsSeeThePageInTheirMenu() throws Exception
    {
        // The tenant administrator's role allows the whole system module, so new pages of it show up in the menu.
        mvc.perform(get("/api/v1/me/authorization").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources").value(Matchers.hasItems("system.sod", "system.sod.btn.create",
                        "system.identity-source")));
    }

    @Test
    void reportsConstraintsThatCannotWork() throws Exception
    {
        String payer = role("lonely-" + System.nanoTime() % 100_000, "Lonely");
        send(post("/api/v1/sod-constraints"), "{\"code\": \"one\", \"name\": \"One\", \"roleIds\": [\"%s\"]}".formatted(payer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-070"));
        send(post("/api/v1/sod-constraints"), "{\"code\": \"x\", \"roleIds\": []}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sod-conflicts")).andExpect(status().isUnauthorized());
    }
}
