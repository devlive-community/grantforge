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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What catalog changes would do, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-impact",
        "grantforge.setup.token=" + ImpactControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class ImpactControllerTest
{
    static final String TOKEN = "catalog-impact-token-0123456789";

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

    private static String idOf(String json, String code)
    {
        return JsonPath.<List<String>>read(json, "$[?(@.code == '" + code + "')].id").get(0);
    }

    @Test
    void reportsWhatDisablingAButtonAndChangingItsDependenciesWouldDo() throws Exception
    {
        Cookie root = flow.login("root");
        String console = String.valueOf(JsonPath.<List<Object>>read(mvc.perform(get("/api/v1/applications").cookie(root))
                .andReturn().getResponse().getContentAsString(), "$[?(@.code == 'grantforge-console')].id").get(0));
        String catalog = mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root)).andReturn().getResponse()
                .getContentAsString();
        String delete = idOf(catalog, "system.user.btn.delete");
        String read = idOf(catalog, "api:system.role.read");

        // The system roles of every tenant reach the button: root's in the platform tenant, boss's in acme.
        mvc.perform(get("/api/v1/resources/" + delete + "/impact").param("enabled", "false").cookie(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lost").value(hasItem("system.user.btn.delete")))
                .andExpect(jsonPath("$.roles[*].code").value(hasItem("platform-admin")))
                .andExpect(jsonPath("$.roles[*].tenantCode").value(hasItem("acme")))
                .andExpect(jsonPath("$.accounts").value(2));
        mvc.perform(get("/api/v1/resources/" + delete + "/impact").param("enabled", "true").cookie(root))
                .andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(post("/api/v1/resources/" + delete + "/dependencies/impact").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dependsOnId\": \"%s\"}".formatted(read)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
        String dependency = JsonPath.read(mvc.perform(get("/api/v1/resources/" + delete + "/dependencies").cookie(root))
                .andReturn().getResponse().getContentAsString(), "$.requires[0].id");
        mvc.perform(get("/api/v1/resource-dependencies/" + dependency + "/impact").param("kind", "REQUIRED").cookie(root))
                .andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(get("/api/v1/resource-dependencies/42/impact").cookie(root)).andExpect(status().isNotFound());
    }
}
