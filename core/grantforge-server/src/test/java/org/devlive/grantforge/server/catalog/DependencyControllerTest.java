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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Resource dependencies through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-dependencies",
        "grantforge.setup.token=" + DependencyControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class DependencyControllerTest
{
    static final String TOKEN = "catalog-dependencies-token-0123";

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

    private ResultActions depend(Cookie session, String resource, String json) throws Exception
    {
        return mvc.perform(post("/api/v1/resources/" + resource + "/dependencies").with(csrf()).cookie(session)
                .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void buttonsNeedApisAndCyclesAreRefused() throws Exception
    {
        Cookie root = flow.login("root");
        String console = flow.consoleId(root);
        String page = CatalogFlow.idOf(flow.createResource(root, console, "{\"type\": \"PAGE\", \"code\": \"demo-users\", \"name\": \"用户\"}"));
        String edit = CatalogFlow.idOf(flow.createResource(root, console,
                "{\"parentId\": \"%s\", \"type\": \"ACTION\", \"code\": \"demo-users.edit\", \"name\": \"编辑\"}".formatted(page)));
        String view = CatalogFlow.idOf(flow.createResource(root, console,
                "{\"parentId\": \"%s\", \"type\": \"ACTION\", \"code\": \"demo-users.view\", \"name\": \"查看\"}".formatted(page)));
        String resources = mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))
                .andReturn().getResponse().getContentAsString();
        String read = JsonPath.<List<String>>read(resources, "$[?(@.code == 'api:system.user.read')].id").get(0);

        String needsRead = CatalogFlow.idOf(depend(root, edit, "{\"dependsOnId\": \"%s\"}".formatted(read))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("REQUIRED"))
                .andExpect(jsonPath("$.source").value("MANUAL")));
        depend(root, edit, "{\"dependsOnId\": \"%s\", \"kind\": \"OPTIONAL\"}".formatted(view)).andExpect(status().isCreated());
        depend(root, view, "{\"dependsOnId\": \"%s\"}".formatted(edit))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-021"))
                .andExpect(jsonPath("$.detail").value("这条依赖会形成循环：对方已经（间接）依赖当前资源。"));
        depend(root, edit, "{\"dependsOnId\": \"%s\"}".formatted(console)).andExpect(status().isNotFound());
        depend(root, edit, "{}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/resources/" + edit + "/dependencies").cookie(root))
                .andExpect(jsonPath("$.requires.length()").value(2))
                .andExpect(jsonPath("$.requiredBy").isEmpty());
        String aroundRead = mvc.perform(get("/api/v1/resources/" + read + "/dependencies").cookie(root))
                .andReturn().getResponse().getContentAsString();
        // The console's own user page and view button need it too (declared by the manifest).
        assertThat(JsonPath.<List<String>>read(aroundRead, "$.requiredBy[*].resourceId")).contains(edit);
        String graph = mvc.perform(get("/api/v1/applications/" + console + "/dependencies").cookie(root))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(graph, "$[?(@.source == 'MANUAL')].id")).hasSize(2);
        mvc.perform(put("/api/v1/resource-dependencies/" + needsRead).with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\": \"OPTIONAL\"}"))
                .andExpect(jsonPath("$.kind").value("OPTIONAL"));
        mvc.perform(delete("/api/v1/resources/" + view).with(csrf()).cookie(root))
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-016"));

        Cookie boss = flow.login("boss");
        mvc.perform(get("/api/v1/resources/" + edit + "/dependencies").cookie(boss)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/resource-dependencies/" + needsRead).with(csrf()).cookie(boss))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/resource-dependencies/" + needsRead).with(csrf()).cookie(root))
                .andExpect(status().isNoContent());
        String after = mvc.perform(get("/api/v1/resources/" + edit + "/dependencies").cookie(root))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(after, "$.requires[*].dependsOnId")).containsExactly(view);
    }
}
