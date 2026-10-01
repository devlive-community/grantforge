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

/** Applications of the resource catalog through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-applications",
        "grantforge.setup.token=" + ApplicationControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class ApplicationControllerTest
{
    static final String TOKEN = "catalog-applications-token-0123";

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
    void theConsoleIsRegisteredAndPlatformAdministratorsManageApplications() throws Exception
    {
        Cookie root = flow.login("root");
        mvc.perform(get("/api/v1/applications").cookie(root))
                .andExpect(jsonPath("$[0].code").value("grantforge-console"))
                .andExpect(jsonPath("$[0].builtin").value(true));

        String crm = CatalogFlow.idOf(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"crm\", \"name\": \"CRM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resources").value(0)));
        mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"crm\", \"name\": \"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-001"))
                .andExpect(jsonPath("$.detail").value("应用编码“crm”已被使用。"));
        mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/applications/" + crm).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Customers\", \"description\": \"Sales\"}"))
                .andExpect(jsonPath("$.name").value("Customers"))
                .andExpect(jsonPath("$.description").value("Sales"));

        flow.createResource(root, crm, "{\"type\": \"MODULE\", \"code\": \"sales\", \"name\": \"Sales\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/applications/" + crm + "/resources").cookie(root))
                .andExpect(jsonPath("$[0].code").value("sales"))
                .andExpect(jsonPath("$[0].applicationId").value(crm))
                .andExpect(jsonPath("$[0].visible").value(true))
                .andExpect(jsonPath("$[0].denyMode").value("HIDE"));
        mvc.perform(delete("/api/v1/applications/" + crm).with(csrf()).cookie(root))
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-003"));
        mvc.perform(delete("/api/v1/applications/" + flow.consoleId(root)).with(csrf()).cookie(root))
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-002"));
        mvc.perform(get("/api/v1/applications/not-an-id/resources").cookie(root)).andExpect(status().isNotFound());
    }

    @Test
    void tenantAdministratorsOnlyReadTheCatalog() throws Exception
    {
        Cookie root = flow.login("root");
        String crm = CatalogFlow.idOf(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"erp\", \"name\": \"ERP\"}")));
        Cookie boss = flow.login("boss");

        mvc.perform(get("/api/v1/applications").cookie(boss)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/applications/" + crm + "/resources").cookie(boss)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/applications").with(csrf()).cookie(boss).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"mine\", \"name\": \"Mine\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + crm).with(csrf()).cookie(boss)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + crm).with(csrf()).cookie(root)).andExpect(status().isNoContent());
    }
}
