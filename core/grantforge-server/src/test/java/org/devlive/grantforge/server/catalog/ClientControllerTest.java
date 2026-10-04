// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OAuth clients of catalog applications through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-clients",
        "grantforge.setup.token=" + ClientControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class ClientControllerTest
{
    static final String TOKEN = "catalog-clients-token-0123456789";

    private static final String WEB = "{\"type\": \"CONFIDENTIAL\", \"settings\": {\"name\": \"CRM web\","
            + " \"redirectUris\": [\"https://crm.example/cb\"], \"scopes\": [\"openid\"],"
            + " \"grants\": [\"AUTHORIZATION_CODE\", \"REFRESH_TOKEN\"]}}";

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
    void platformAdministratorsRegisterRotateAndDeleteClients() throws Exception
    {
        Cookie root = flow.login("root");
        String crm = CatalogFlow.idOf(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"crm\", \"name\": \"CRM\"}")));

        MvcResult created = mvc.perform(post("/api/v1/applications/" + crm + "/clients").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content(WEB))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.secret").isString())
                .andExpect(jsonPath("$.client.applicationId").value(crm))
                .andExpect(jsonPath("$.client.accessTokenMinutes").value(15))
                .andExpect(jsonPath("$.client.refreshTokenHours").value(720))
                .andExpect(jsonPath("$.client.grants[0]").value("AUTHORIZATION_CODE"))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.client.id");

        mvc.perform(get("/api/v1/applications/" + crm + "/clients").cookie(root))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
        mvc.perform(put("/api/v1/clients/" + id).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"CRM\", \"grants\": [\"CLIENT_CREDENTIALS\"], \"accessTokenMinutes\": 60, \"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.accessTokenMinutes").value(60));
        mvc.perform(post("/api/v1/clients/" + id + "/rotate-secret").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"graceHours\": 24}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.secret").isString())
                .andExpect(jsonPath("$.client.previousSecretExpiresAt").isString());
        mvc.perform(post("/api/v1/clients/" + id + "/rotate-secret").with(csrf()).cookie(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.previousSecretExpiresAt").doesNotExist());
        mvc.perform(delete("/api/v1/clients/" + id).with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/applications/" + crm + "/clients").cookie(root)).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refusesWrongSettingsInTheUsersLanguageAndOtherTenants() throws Exception
    {
        Cookie root = flow.login("root");
        String crm = CatalogFlow.idOf(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"erp\", \"name\": \"ERP\"}")));

        mvc.perform(post("/api/v1/applications/" + crm + "/clients").with(csrf()).cookie(root).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\": \"PUBLIC\", \"settings\": {\"name\": \"SPA\","
                                + " \"redirectUris\": [\"http://spa.example/cb\"], \"grants\": [\"CLIENT_CREDENTIALS\"]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-060"))
                .andExpect(jsonPath("$.detail").value("客户端的部分设置不正确。"))
                .andExpect(jsonPath("$.errors[0].field").value("redirectUris[0]"))
                .andExpect(jsonPath("$.errors[1].field").value("grants"))
                .andExpect(jsonPath("$.errors[1].message").value("只有机密客户端才能以自身身份获取令牌。"));
        mvc.perform(post("/api/v1/applications/" + crm + "/clients").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/applications/" + flow.consoleId(root) + "/clients").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content(WEB)).andExpect(jsonPath("$.code").value("GF-AUTHZ-060"));
        mvc.perform(put("/api/v1/clients/not-an-id").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"x\", \"grants\": [\"CLIENT_CREDENTIALS\"]}")).andExpect(status().isNotFound());

        Cookie boss = flow.login("boss");
        mvc.perform(get("/api/v1/applications/" + crm + "/clients").cookie(boss)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/applications/" + crm + "/clients").with(csrf()).cookie(boss).contentType(MediaType.APPLICATION_JSON)
                .content(WEB)).andExpect(status().isForbidden());
    }
}
