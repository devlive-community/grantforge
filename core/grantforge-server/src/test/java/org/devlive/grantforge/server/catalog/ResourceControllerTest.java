// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The resource tree of the console application through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-resources",
        "grantforge.setup.token=" + ResourceControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class ResourceControllerTest
{
    static final String TOKEN = "catalog-resources-token-01234567";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private CatalogFlow flow;

    @BeforeEach
    void setUp() throws Exception
    {
        flow = new CatalogFlow(mvc, jdbc, TOKEN);
    }

    @Test
    void platformAdministratorsBuildTheTree() throws Exception
    {
        Cookie root = flow.login("root");
        String console = flow.consoleId(root);
        String system = CatalogFlow.idOf(flow.createResource(root, console,
                "{\"type\": \"MODULE\", \"code\": \"system\", \"name\": \"系统管理\"}").andExpect(status().isCreated()));
        String audit = CatalogFlow.idOf(flow.createResource(root, console,
                "{\"type\": \"MODULE\", \"code\": \"audit\", \"name\": \"审计\"}"));
        String users = CatalogFlow.idOf(flow.createResource(root, console, """
                {"parentId": "%s", "type": "PAGE", "code": "system.user.list", "name": "用户管理", "route": "/admin/users"}
                """.formatted(system)).andExpect(jsonPath("$.parentId").value(system)).andExpect(jsonPath("$.depth").value(1)));
        String export = CatalogFlow.idOf(flow.createResource(root, console, """
                {"parentId": "%s", "type": "ACTION", "code": "system.user.btn.export", "name": "导出", "denyMode": "DISABLE"}
                """.formatted(users)).andExpect(jsonPath("$.denyMode").value("DISABLE")));

        flow.createResource(root, console, "{\"type\": \"ACTION\", \"code\": \"loose\", \"name\": \"Loose\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-011"));
        flow.createResource(root, console, "{\"type\": \"MODULE\", \"code\": \"system\", \"name\": \"Again\"}")
                .andExpect(jsonPath("$.detail").value("资源编码“system”在该应用中已被使用。"));
        flow.createResource(root, console, "{\"code\": \"typeless\", \"name\": \"No type\"}").andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/resources/" + users).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"system.users\", \"name\": \"用户\", \"route\": \"/admin/users\", \"visible\": false}"))
                .andExpect(jsonPath("$.code").value("system.users"))
                .andExpect(jsonPath("$.visible").value(false));
        mvc.perform(post("/api/v1/resources/" + users + "/move").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"parentId\": \"%s\", \"position\": 0}".formatted(audit)))
                .andExpect(jsonPath("$.parentId").value(audit));
        mvc.perform(post("/api/v1/resources/" + export + "/move").with(csrf()).cookie(root)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\": null, \"position\": 0}"))
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-011"))
                .andExpect(jsonPath("$.detail").value("该类型的资源不能放在这里：按钮和标签页属于页面，字段属于数据实体。"));
        mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))
                // The other test of this class may have added resources to the shared database.
                .andExpect(jsonPath("$[?(@.code == 'system.users')].parentId").value(audit))
                .andExpect(jsonPath("$[?(@.code == 'system.user.btn.export')].depth").value(2));

        mvc.perform(delete("/api/v1/resources/" + audit).with(csrf()).cookie(root))
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-014"));
        mvc.perform(delete("/api/v1/resources/" + export).with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/resources/" + export).with(csrf()).cookie(root)).andExpect(status().isNotFound());
    }

    @Test
    void tenantAdministratorsCannotChangeResources() throws Exception
    {
        Cookie root = flow.login("root");
        String console = flow.consoleId(root);
        String module = CatalogFlow.idOf(flow.createResource(root, console,
                "{\"type\": \"MODULE\", \"code\": \"reports\", \"name\": \"Reports\"}"));
        Cookie boss = flow.login("boss");

        flow.createResource(boss, console, "{\"type\": \"MODULE\", \"code\": \"mine\", \"name\": \"Mine\"}")
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/resources/" + module).with(csrf()).cookie(boss).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"reports\", \"name\": \"Mine\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/resources/" + module + "/move").with(csrf()).cookie(boss)
                .contentType(MediaType.APPLICATION_JSON).content("{\"position\": 0}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/resources/" + module).with(csrf()).cookie(boss)).andExpect(status().isForbidden());
    }
}
