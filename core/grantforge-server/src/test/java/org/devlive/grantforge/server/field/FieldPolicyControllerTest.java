// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Field policies of roles, and the hiding and masking they cause, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fieldpolicies",
        "grantforge.setup.token=" + FieldPolicyControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class FieldPolicyControllerTest
{
    static final String TOKEN = "field-token-0123456789abcdef";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

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
            TenantContext.runInTenant(platform, () -> accounts.save(UserAccount.create("viewer", encoder.encode(PASSWORD),
                    Instant.now()).withEmail("viewer@acme.io")));
        }
    }

    private Cookie login(String username) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private static String body(ResultActions result) throws Exception
    {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String role(Cookie root) throws Exception
    {
        String role = JsonPath.read(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"mail-readers\", \"name\": \"Mail readers\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        List<?> consoles = JsonPath.read(body(mvc.perform(get("/api/v1/applications").cookie(root))),
                "$[?(@.code == 'grantforge-console')].id");
        String console = String.valueOf(consoles.get(0));
        List<?> apis = JsonPath.read(body(mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))),
                "$[?(@.code == 'api:system.user.read' || @.code == 'api:system.user.export')].id");
        mvc.perform(put("/api/v1/roles/" + role + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": \"%s\", \"changes\": [{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"}, ".formatted(console,
                        apis.get(0)) + "{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"}]}".formatted(apis.get(1))))
                .andExpect(status().isOk());
        for (String action : List.of("READ", "EXPORT")) {
            mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"entityCode\": \"user\", \"action\": \"%s\", \"scope\": \"TENANT\"}".formatted(action)))
                    .andExpect(status().isCreated());
        }
        String viewerId = String.valueOf(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'viewer'", Long.class));
        mvc.perform(post("/api/v1/roles/" + role + "/assignments").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectType\": \"USER\", \"subjectId\": \"%s\"}".formatted(viewerId))).andExpect(status().isCreated());
        return role;
    }

    @Test
    void rolesHideAndMaskTheSecuredFieldsOfResponsesAndExports() throws Exception
    {
        Cookie root = login("root");
        String role = role(root);
        Cookie viewer = login("viewer");
        mvc.perform(get("/api/v1/users").param("q", "viewer").cookie(viewer)).andExpect(jsonPath("$.items[0].email").value("viewer@acme.io"))
                .andExpect(jsonPath("$.items[0].lastLoginAt").exists());

        mvc.perform(put("/api/v1/roles/" + role + "/field-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"policies": [{"entityCode": "user", "fieldCode": "email", "readMode": "MASKED", "maskStrategy": "EMAIL"},
                                      {"entityCode": "user", "fieldCode": "lastLoginAt", "readMode": "HIDDEN"}]}
                        """)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fieldCode").value("email")).andExpect(jsonPath("$[0].writeMode").value("EDITABLE"));
        mvc.perform(get("/api/v1/roles/" + role + "/field-policies").cookie(root)).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].readMode").value("HIDDEN"));

        mvc.perform(get("/api/v1/users").param("q", "viewer").cookie(viewer)).andExpect(jsonPath("$.items[0].email").value("v***@acme.io"))
                .andExpect(jsonPath("$.items[0].lastLoginAt").doesNotExist());
        // Searching what is masked finds nothing, so a search cannot reveal it.
        mvc.perform(get("/api/v1/users").param("q", "acme.io").cookie(viewer)).andExpect(jsonPath("$.total").value(0));
        String csv = body(mvc.perform(get("/api/v1/users/export").param("q", "viewer").cookie(viewer)));
        assertThat(csv).contains("v***@acme.io").doesNotContain("viewer@acme.io");

        // The built-in administrator sees every field.
        mvc.perform(get("/api/v1/users").param("q", "acme.io").cookie(root)).andExpect(jsonPath("$.items[0].email").value("viewer@acme.io"));
    }

    @Test
    void refusesPoliciesThatDoNotFit() throws Exception
    {
        Cookie root = login("root");
        String role = JsonPath.read(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"wrong-fields\", \"name\": \"Wrong fields\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/v1/roles/" + role + "/field-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policies\": [{\"entityCode\": \"user\", \"fieldCode\": \"password\", \"readMode\": \"HIDDEN\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GF-AUTHZ-051"))
                .andExpect(jsonPath("$.errors[0].field").value("policies[0].fieldCode"));
        mvc.perform(put("/api/v1/roles/" + role + "/field-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"policies\": [{\"entityCode\": \"user\", \"fieldCode\": \"email\"}]}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/roles/424242/field-policies").cookie(root)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/roles/" + role + "/field-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
