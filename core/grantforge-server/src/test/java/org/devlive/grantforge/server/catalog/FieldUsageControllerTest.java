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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Where secured fields appear, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-field-usages",
        "grantforge.setup.token=" + FieldUsageControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class FieldUsageControllerTest
{
    static final String TOKEN = "catalog-field-usages-token-0123";

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
    void listsTheApisThatReturnOrAcceptAField() throws Exception
    {
        Cookie root = flow.login("root");
        String console = flow.consoleId(root);
        String resources = mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))
                .andReturn().getResponse().getContentAsString();
        String entity = JsonPath.<List<String>>read(resources, "$[?(@.code == 'entity:user')].id").get(0);
        String email = JsonPath.<List<String>>read(resources, "$[?(@.code == 'entity:user.email')].id").get(0);
        mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))
                .andExpect(jsonPath("$[?(@.code == 'entity:user.email')].parentId", hasItem(entity)))
                .andExpect(jsonPath("$[?(@.code == 'entity:user.email')].type", hasItem("FIELD")));

        mvc.perform(get("/api/v1/resources/" + email + "/field-usages").cookie(root)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.httpMethod == 'GET' && @.pathPattern == '/api/v1/users')].direction", hasItem("READ")))
                .andExpect(jsonPath("$[?(@.httpMethod == 'PUT' && @.pathPattern == '/api/v1/users/{id}')].direction",
                        hasItem("WRITE")))
                .andExpect(jsonPath("$[?(@.pathPattern == '/api/v1/groups/{id}/members')].direction", hasItem("READ")));
        mvc.perform(get("/api/v1/resources/" + entity + "/field-usages").cookie(root)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/resources/" + email + "/field-usages")).andExpect(status().isUnauthorized());
    }
}
