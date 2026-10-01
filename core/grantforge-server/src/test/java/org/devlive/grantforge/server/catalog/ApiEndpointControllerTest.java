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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API catalog through the assembled server, filled at start-up from the controllers. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-endpoints",
        "grantforge.setup.token=" + ApiEndpointControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class ApiEndpointControllerTest
{
    static final String TOKEN = "catalog-endpoints-token-0123456";

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
    void listsEveryEndpointWithItsAccessAndPlatformAdministratorsReviewChanges() throws Exception
    {
        Cookie root = flow.login("root");
        String body = mvc.perform(get("/api/v1/api-endpoints").cookie(root)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.pathPattern == '/api/v1/bootstrap')].access").value("PUBLIC"))
                .andExpect(jsonPath("$[?(@.pathPattern == '/api/v1/me' && @.httpMethod == 'GET')].access").value("AUTHENTICATED"))
                .andExpect(jsonPath("$[?(@.pathPattern == '/api/v1/users/{id}' && @.httpMethod == 'GET')].permission")
                        .value("system.user.read"))
                .andReturn().getResponse().getContentAsString();
        List<String> pending = JsonPath.read(body, "$[?(@.change != null)].id");
        assertThat(pending).isNotEmpty();
        // The permission became a built-in API resource of the console.
        String resources = mvc.perform(get("/api/v1/applications/" + flow.consoleId(root) + "/resources").cookie(root))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(resources, "$[?(@.type == 'API')].code")).contains("api:system.user.read",
                "api:platform.api.review");

        Cookie boss = flow.login("boss");
        mvc.perform(get("/api/v1/api-endpoints").cookie(boss)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/api-endpoints/review").with(csrf()).cookie(boss).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpointIds\": [\"%s\"]}".formatted(pending.get(0)))).andExpect(status().isForbidden());

        String ids = String.join("\", \"", pending);
        mvc.perform(post("/api/v1/api-endpoints/review").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpointIds\": [\"%s\"]}".formatted(ids)))
                .andExpect(jsonPath("$.reviewed").value(pending.size()));
        mvc.perform(get("/api/v1/api-endpoints").cookie(root)).andExpect(jsonPath("$[?(@.change != null)]").isEmpty());
        mvc.perform(post("/api/v1/api-endpoints/review").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpointIds\": [\"not-an-id\"]}")).andExpect(status().isNotFound());
    }
}
