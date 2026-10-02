// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.authz.application.DeclaredEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.catalog.ApiEndpointScanner;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every API that needs a permission, called by each kind of caller, and permissions changing between calls. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:permission-matrix",
        "grantforge.setup.token=" + PermissionMatrixTest.TOKEN,
})
@AutoConfigureMockMvc
class PermissionMatrixTest
{
    static final String TOKEN = "permission-matrix-token-0123456789";
    private static final String PASSWORD = "a long enough password";
    private static final String BOSS_PASSWORD = "a fresh and private phrase";
    private static final String DENIED = SecurityErrorCode.PERMISSION_DENIED.code();
    /** Platform permissions tenant administrators hold too: granting roles shows them the resource catalog. */
    private static final Set<String> SHARED_WITH_TENANTS = Set.of("platform.catalog.read");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApiEndpointScanner scanner;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
            long platform = requireNonNull(jdbc.queryForObject("SELECT id FROM gf_tenant", Long.class));
            TenantContext.runInTenant(platform, () -> accounts.save(UserAccount.create("reader", encoder.encode(PASSWORD),
                    Instant.now())));
            TenantContext.runInTenant(platform, () -> accounts.save(UserAccount.create("manager", encoder.encode(PASSWORD),
                    Instant.now())));
            mvc.perform(post("/api/v1/tenants").with(csrf()).cookie(login("root")).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"code": "acme", "name": "Acme", "adminUsername": "boss", "adminDisplayName": "Boss",
                             "adminPassword": "%s"}
                            """.formatted(PASSWORD))).andExpect(status().isCreated());
            // The new tenant's administrator must choose their own password before anything else.
            mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(login("boss", PASSWORD))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(PASSWORD, BOSS_PASSWORD)))
                    .andExpect(status().isNoContent());
        }
    }

    private Cookie login(String username) throws Exception
    {
        return login(username, PASSWORD);
    }

    private Cookie login(String username, String password) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private MockHttpServletResponse call(DeclaredEndpoint endpoint, @Nullable Cookie session) throws Exception
    {
        String path = endpoint.pathPattern().replaceAll("\\{[^}]+}", "1");
        // Imports take files; anything else is refused before a handler is chosen.
        MediaType type = path.endsWith("/import") ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_JSON;
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(endpoint.httpMethod()), path).with(csrf())
                .contentType(type).content("{}");
        if (session != null) {
            builder.cookie(session);
        }
        return mvc.perform(builder).andReturn().getResponse();
    }

    private static boolean denied(MockHttpServletResponse response) throws Exception
    {
        return response.getStatus() == 403 && response.getContentAsString().contains(DENIED);
    }

    @Test
    void everyGuardedApiAnswersEachKindOfCaller() throws Exception
    {
        List<DeclaredEndpoint> guarded = scanner.scan().stream()
                .filter(endpoint -> endpoint.declaration().access() == EndpointAccess.PERMISSION).toList();
        assertThat(guarded).hasSizeGreaterThan(40);
        Cookie root = login("root");
        Cookie boss = login("boss", BOSS_PASSWORD);
        Cookie reader = login("reader");

        for (DeclaredEndpoint endpoint : guarded) {
            String permission = String.valueOf(endpoint.declaration().permission());
            String name = endpoint.httpMethod() + " " + endpoint.pathPattern() + " (" + permission + ")";
            assertThat(call(endpoint, null).getStatus()).as("anonymous " + name).isEqualTo(401);
            assertThat(denied(call(endpoint, reader))).as("account without roles " + name).isTrue();
            assertThat(denied(call(endpoint, root))).as("platform administrator " + name).isFalse();
            assertThat(denied(call(endpoint, boss))).as("tenant administrator " + name)
                    .isEqualTo(permission.startsWith("platform.") && !SHARED_WITH_TENANTS.contains(permission));
        }
    }

    @Test
    void grantsAndAssignmentsApplyToTheNextCall() throws Exception
    {
        Cookie root = login("root");
        Cookie reader = login("reader");
        String readerId = String.valueOf(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'reader'",
                Long.class));
        String role = idOf(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"org-readers\", \"name\": \"Org readers\"}")).andExpect(status().isCreated()));
        List<?> consoles = JsonPath.read(body(mvc.perform(get("/api/v1/applications").cookie(root))),
                "$[?(@.code == 'grantforge-console')].id");
        String console = String.valueOf(consoles.get(0));
        List<?> page = JsonPath.read(body(mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))),
                "$[?(@.code == 'system.org')].id");
        mvc.perform(put("/api/v1/roles/" + role + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": \"%s\", \"changes\": [{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"}]}"
                        .formatted(console, page.get(0)))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/org-units").cookie(reader)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(DENIED));
        String assignment = idOf(mvc.perform(post("/api/v1/roles/" + role + "/assignments").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"subjectType\": \"USER\", \"subjectId\": \"%s\"}"
                        .formatted(readerId))).andExpect(status().isCreated()));
        String version = String.valueOf(JsonPath.<Object>read(body(mvc.perform(get("/api/v1/me/authorization").cookie(reader))),
                "$.version"));
        mvc.perform(get("/api/v1/org-units").cookie(reader)).andExpect(status().isOk())
                .andExpect(header().string(PermissionGuard.VERSION_HEADER, version));
        mvc.perform(post("/api/v1/org-units").with(csrf()).cookie(reader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"x\", \"name\": \"X\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/authorization").cookie(reader))
                .andExpect(jsonPath("$.roles[0]").value("org-readers"))
                .andExpect(jsonPath("$.permissions[0]").value("system.org.read"));

        mvc.perform(post("/api/v1/roles/" + role + "/disable").with(csrf()).cookie(root)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/org-units").cookie(reader)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/roles/" + role + "/enable").with(csrf()).cookie(root)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/org-units").cookie(reader)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/role-assignments/" + assignment).with(csrf()).cookie(root)).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/v1/org-units").cookie(reader)).andExpect(status().isForbidden());
    }

    @Test
    void anyAccountWhoseRolesAllowItManagesRolesNotJustTheBuiltInAdministrator() throws Exception
    {
        Cookie root = login("root");
        Cookie manager = login("manager");
        String managerId = String.valueOf(jdbc.queryForObject(
                "SELECT id FROM gf_user_account WHERE username_norm = 'manager'", Long.class));
        String role = idOf(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"role-makers\", \"name\": \"Role makers\"}")).andExpect(status().isCreated()));
        List<?> consoles = JsonPath.read(body(mvc.perform(get("/api/v1/applications").cookie(root))),
                "$[?(@.code == 'grantforge-console')].id");
        String console = String.valueOf(consoles.get(0));
        List<?> button = JsonPath.read(body(mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))),
                "$[?(@.code == 'system.role.btn.create')].id");
        mvc.perform(put("/api/v1/roles/" + role + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": \"%s\", \"changes\": [{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"}]}"
                        .formatted(console, button.get(0)))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/roles/" + role + "/assignments").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"subjectType\": \"USER\", \"subjectId\": \"%s\"}"
                        .formatted(managerId))).andExpect(status().isCreated());

        // The manager is no built-in administrator; the role alone lets them list and create roles, nothing more.
        mvc.perform(get("/api/v1/roles").cookie(manager)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/roles").with(csrf()).cookie(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"helpers\", \"name\": \"Helpers\"}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/roles/" + role).with(csrf()).cookie(manager)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(DENIED));
    }

    private static String body(ResultActions result) throws Exception
    {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String idOf(ResultActions created) throws Exception
    {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
