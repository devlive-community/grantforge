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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void platformAdministratorRoleCanClearWithoutSystemAccount() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "clear-role-admin");
        String top = CatalogFlow.idOf(flow.createResource(root, app, "{\"type\":\"MODULE\",\"code\":\"root\",\"name\":\"Root\"}")
                .andExpect(status().isCreated()));
        flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"PAGE\",\"code\":\"child\",\"name\":\"Child\"}".formatted(top))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE gf_user_account SET system_account = false WHERE username = ?", "root");
        try {
            mvc.perform(get("/api/v1/me/authorization").cookie(root)).andExpect(jsonPath("$.unrestricted").value(false));
            mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree/" + top + "/children").with(csrf()).cookie(root))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(1)));
        }
        finally {
            jdbc.update("UPDATE gf_user_account SET system_account = true WHERE username = ?", "root");
        }
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

    @Test
    void clearsOnlyDescendantsAndRequiresPlatformAdministrator() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "clear-branch");
        String top = CatalogFlow.idOf(flow.createResource(root, app, "{\"type\":\"MODULE\",\"code\":\"one\",\"name\":\"One\"}")
                .andExpect(status().isCreated()));
        String page = CatalogFlow.idOf(flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"PAGE\",\"code\":\"one.page\",\"name\":\"Page\"}".formatted(top))
                .andExpect(status().isCreated()));
        flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"ACTION\",\"code\":\"one.action\",\"name\":\"Action\"}".formatted(page))
                .andExpect(status().isCreated());
        String api = CatalogFlow.idOf(flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"API\",\"code\":\"one.api\",\"name\":\"API\"}".formatted(top))
                .andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/resources/" + page + "/dependencies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dependsOnId\":\"%s\"}".formatted(api))).andExpect(status().isCreated());
        String other = CatalogFlow.idOf(flow.createResource(root, app, "{\"type\":\"MODULE\",\"code\":\"two\",\"name\":\"Two\"}")
                .andExpect(status().isCreated()));
        flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"PAGE\",\"code\":\"two.page\",\"name\":\"Other\"}".formatted(other))
                .andExpect(status().isCreated());
        String endpoint = "/api/v1/applications/" + app + "/resource-tree/" + top + "/children";
        mvc.perform(delete(endpoint).with(csrf()).cookie(flow.login("boss"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + flow.consoleId(root) + "/resource-tree/" + top + "/children").with(csrf()).cookie(root))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree/" + page + "/children").with(csrf()).cookie(root))
                .andExpect(status().isNotFound());
        mvc.perform(delete(endpoint).with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(top)).andExpect(jsonPath("$[1].id").value(other));
    }

    @Test
    void dependencyOutsideBranchPreventsClearingAndRollsBack() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "clear-branch-dependency");
        String top = CatalogFlow.idOf(flow.createResource(root, app, "{\"type\":\"MODULE\",\"code\":\"one\",\"name\":\"One\"}")
                .andExpect(status().isCreated()));
        String page = CatalogFlow.idOf(flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"PAGE\",\"code\":\"one.page\",\"name\":\"Page\"}".formatted(top))
                .andExpect(status().isCreated()));
        String api = CatalogFlow.idOf(flow.createResource(root, app, "{\"type\":\"API\",\"code\":\"outside.api\",\"name\":\"API\"}")
                .andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/resources/" + page + "/dependencies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dependsOnId\":\"%s\"}".formatted(api))).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree/" + top + "/children").with(csrf()).cookie(root))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void declaredDependencyPreventsClearingDescendants() throws Exception
    {
        Cookie root = flow.login("root");
        String app = application(root, "clear-branch-declared");
        submit(root, app, true, DOCUMENT).andExpect(jsonPath("$.applied").value(true));
        String body = mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root))
                .andReturn().getResponse().getContentAsString();
        String top = com.jayway.jsonpath.JsonPath.read(body, "$[0].id");
        jdbc.update("UPDATE gf_resource_dependency SET dependency_source = 'DECLARED' WHERE application_id = ?", Long.parseLong(app));
        mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree/" + top + "/children").with(csrf()).cookie(root))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void clearingReportsEveryBlockingRoleAndDirectUserGrant() throws Exception
    {
        Cookie root = flow.login("root");
        Cookie boss = flow.login("boss");
        String app = application(root, "clear-granted-details");
        String top = CatalogFlow.idOf(flow.createResource(root, app, "{\"type\":\"MODULE\",\"code\":\"reports\",\"name\":\"Reports\"}")
                .andExpect(status().isCreated()));
        String first = CatalogFlow.idOf(flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"PAGE\",\"code\":\"reports.read\",\"name\":\"Read reports\"}".formatted(top))
                .andExpect(status().isCreated()));
        String second = CatalogFlow.idOf(flow.createResource(root, app, "{\"parentId\":\"%s\",\"type\":\"PAGE\",\"code\":\"reports.edit\",\"name\":\"Edit reports\"}".formatted(top))
                .andExpect(status().isCreated()));
        String platformRole = CatalogFlow.idOf(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"report-readers\",\"name\":\"Report readers\"}")).andExpect(status().isCreated()));
        String tenantRole = CatalogFlow.idOf(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(boss).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"report-editors\",\"name\":\"Report editors\"}")).andExpect(status().isCreated()));
        for (String[] grant : new String[][] {{platformRole, first}, {tenantRole, second}}) {
            Cookie actor = grant[0].equals(platformRole) ? root : boss;
            mvc.perform(put("/api/v1/roles/" + grant[0] + "/grants").with(csrf()).cookie(actor).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"applicationId\":\"%s\",\"changes\":[{\"resourceId\":\"%s\",\"effect\":\"ALLOW\"}]}".formatted(app, grant[1])))
                    .andExpect(status().isOk());
        }
        String rootId = com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/api/v1/users").param("q", "root").cookie(root))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.items[0].id");
        mvc.perform(put("/api/v1/users/" + rootId + "/resource-grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\":\"%s\",\"changes\":[{\"resourceId\":\"%s\",\"effect\":\"ALLOW\"}]}".formatted(app, first)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/applications/" + app + "/resource-tree/" + top + "/children").with(csrf()).cookie(root)
                .header("Accept-Language", "zh-CN"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail", containsString("Report readers")))
                .andExpect(jsonPath("$.detail", containsString("Report editors")))
                .andExpect(jsonPath("$.detail", containsString("reports.read")))
                .andExpect(jsonPath("$.detail", containsString("reports.edit")))
                .andExpect(jsonPath("$.detail", containsString("用户直接授权")))
                .andExpect(jsonPath("$.detail", containsString("acme")));
        mvc.perform(get("/api/v1/applications/" + app + "/resources").cookie(root)).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void applicationAdministratorIsRestrictedToAssignedApplication() throws Exception
    {
        Cookie root = flow.login("root");
        String user = com.jayway.jsonpath.JsonPath.read(mvc.perform(post("/api/v1/users").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"app-manager\",\"password\":\"%s\",\"profile\":{}}".formatted(CatalogFlow.PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.user.id");
        String managed = CatalogFlow.idOf(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"managed-app\",\"name\":\"Managed\",\"administratorId\":\"%s\"}".formatted(user)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.administratorId").value(user)));
        String other = application(root, "other-app");
        String otherResource = CatalogFlow.idOf(flow.createResource(root, other, "{\"type\":\"MODULE\",\"code\":\"other\",\"name\":\"Other\"}")
                .andExpect(status().isCreated()));
        Cookie manager = flow.login("app-manager");
        mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s!\"}".formatted(CatalogFlow.PASSWORD, CatalogFlow.PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/authorization").cookie(manager))
                .andExpect(jsonPath("$.roles[0]").value("application-admin"));
        mvc.perform(get("/api/v1/applications").cookie(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(managed));
        String top = CatalogFlow.idOf(flow.createResource(manager, managed, "{\"type\":\"MODULE\",\"code\":\"managed\",\"name\":\"Managed\"}")
                .andExpect(status().isCreated()));
        flow.createResource(manager, managed, "{\"parentId\":\"%s\",\"type\":\"PAGE\",\"code\":\"managed.page\",\"name\":\"Page\"}".formatted(top))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/applications/" + managed + "/resource-tree/" + top + "/children").with(csrf()).cookie(manager))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/applications/" + other + "/resources").cookie(manager)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/applications/" + other + "/clients").cookie(manager)).andExpect(status().isForbidden());
        String clientRequest = "{\"type\":\"PUBLIC\",\"settings\":{\"name\":\"SPA\",\"redirectUris\":[\"http://localhost:3000/cb\"],\"scopes\":[\"openid\"],\"grants\":[\"AUTHORIZATION_CODE\"]}}";
        mvc.perform(post("/api/v1/applications/" + managed + "/clients").with(csrf()).cookie(manager)
                .contentType(MediaType.APPLICATION_JSON).content(clientRequest)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/applications/" + other + "/clients").with(csrf()).cookie(manager)
                .contentType(MediaType.APPLICATION_JSON).content(clientRequest)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/resources/" + otherResource).with(csrf()).cookie(manager)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + other + "/resource-tree").with(csrf()).cookie(manager)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/applications").with(csrf()).cookie(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"forbidden-app\",\"name\":\"Forbidden\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/applications/" + managed).with(csrf()).cookie(manager)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/applications/" + managed).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.administratorId").value(user));
        mvc.perform(put("/api/v1/applications/" + managed).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\",\"administratorId\":\"\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/authorization").cookie(manager)).andExpect(jsonPath("$.roles", hasSize(0)));
        mvc.perform(get("/api/v1/applications").cookie(manager)).andExpect(status().isForbidden());
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
