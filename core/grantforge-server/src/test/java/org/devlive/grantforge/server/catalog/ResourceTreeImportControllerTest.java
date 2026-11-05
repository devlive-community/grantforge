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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Import previews, atomicity, and repeated imports through the API. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-tree-import",
        "grantforge.setup.token=" + ResourceTreeImportControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class ResourceTreeImportControllerTest
{
    static final String TOKEN = "catalog-applications-token-0123";
    private static final String DOCUMENT = """
            {"formatVersion":1,"resources":[{"type":"MODULE","code":"sales","name":"Sales","children":[
              {"type":"PAGE","code":"sales.page","name":"Page","route":"/sales","children":[]},
              {"type":"API","code":"sales.api","name":"API","children":[]}
            ]}],"dependencies":[{"resourceCode":"sales.page","dependsOnCode":"sales.api","kind":"REQUIRED","source":"DECLARED"}]}
            """;

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
    void previewRollsBackAndImportCanBeRepeated() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "import-repeat");
        submit(root, app, false, DOCUMENT).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3)).andExpect(jsonPath("$.addedDependencies").value(1))
                .andExpect(jsonPath("$.declaredDependencies").value(1)).andExpect(jsonPath("$.applied").value(false));
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(0)));
        submit(root, app, true, DOCUMENT).andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.addedDependencies").value(1)).andExpect(jsonPath("$.applied").value(true));
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(3)));
        submit(root, app, true, DOCUMENT).andExpect(jsonPath("$.reused").value(3))
                .andExpect(jsonPath("$.reusedDependencies").value(1)).andExpect(jsonPath("$.applied").value(true));
    }

    @Test
    void conflictRollsBackAllChangesAndTenantAdminCannotImport() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "import-conflict");
        flow.createResource(root, app, "{\"type\":\"PAGE\",\"code\":\"sales.api\",\"name\":\"Existing\"}")
                .andExpect(status().isCreated());
        submit(root, app, true, DOCUMENT).andExpect(status().isOk()).andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.problems", hasSize(1)));
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(1)));
        submit(flow.login("boss"), app, true, DOCUMENT).andExpect(status().isForbidden());
        submit(root, flow.consoleId(root), true, DOCUMENT).andExpect(jsonPath("$.problems", hasSize(1)));
    }

    @Test
    void platformAdministratorClearsTreeAndManualDependencies() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "clear-tree");
        submit(root, app, true, DOCUMENT).andExpect(jsonPath("$.applied").value(true));
        mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree").with(csrf()).cookie(flow.login("boss")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + flow.consoleId(root) + "/resource-tree").with(csrf()).cookie(root))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree").with(csrf()).cookie(root))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void protectedDependencyPreventsClearingTheTree() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "clear-protected");
        submit(root, app, true, DOCUMENT).andExpect(jsonPath("$.applied").value(true));
        jdbc.update("UPDATE gf_resource_dependency SET dependency_source = 'DECLARED' WHERE application_id = ?", Long.parseLong(app));
        mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree").with(csrf()).cookie(root))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(3)));
    }

    private String application(Cookie root, String code) throws Exception
    {
        return CatalogFlow.idOf(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"name\":\"Test\"}"))
                .andExpect(status().isCreated()));
    }

    private ResultActions submit(Cookie session, String app, boolean apply, String json) throws Exception
    {
        return mvc.perform(post("/api/v1/applications/" + app + "/resource-tree/import").param("apply", Boolean.toString(apply))
                .with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
