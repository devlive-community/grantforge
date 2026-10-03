// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.authz.data.DataScopes;
import org.devlive.grantforge.authz.field.FieldPolicies;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.RowScopes;
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

/** The built-in lists keep to the rows readers' data policies allow, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:datascopedlists",
        "grantforge.setup.token=" + DataScopedListsTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class DataScopedListsTest
{
    static final String TOKEN = "scoped-token-0123456789abcdef";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private RowScopes scopes;

    @Autowired
    private FieldRules fields;

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
                    Instant.now())));
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
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String idOf(ResultActions created) throws Exception
    {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void theServerLimitsRowsByDataPoliciesAndFieldsByFieldPolicies()
    {
        assertThat(scopes).isInstanceOf(DataScopes.class);
        assertThat(fields).isInstanceOf(FieldPolicies.class);
    }

    @Test
    void readersSeeAndChangeOnlyTheRowsTheirDataPoliciesAllow() throws Exception
    {
        Cookie root = login("root");
        String viewerId = String.valueOf(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'viewer'",
                Long.class));
        String rootId = String.valueOf(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'root'",
                Long.class));
        String role = idOf(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"self-viewers\", \"name\": \"Self viewers\"}")));
        List<?> consoles = JsonPath.read(body(mvc.perform(get("/api/v1/applications").cookie(root))),
                "$[?(@.code == 'grantforge-console')].id");
        String console = String.valueOf(consoles.get(0));
        List<?> pages = JsonPath.read(body(mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))),
                "$[?(@.code == 'system.user' || @.code == 'system.user.btn.edit')].id");
        mvc.perform(put("/api/v1/roles/" + role + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": \"%s\", \"changes\": [{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"},"
                        .formatted(console, pages.get(0)) + "{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"}]}"
                        .formatted(pages.get(1)))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/roles/" + role + "/assignments").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"subjectType\": \"USER\", \"subjectId\": \"%s\"}"
                        .formatted(viewerId))).andExpect(status().isCreated());
        Cookie viewer = login("viewer");

        // Permissions alone show no rows: rows need a data policy.
        mvc.perform(get("/api/v1/users").cookie(viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityCode\": \"user\", \"action\": \"READ\", \"scope\": \"SELF\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/users").cookie(viewer)).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("viewer"));
        mvc.perform(get("/api/v1/users/" + rootId).cookie(viewer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/" + viewerId).cookie(viewer)).andExpect(status().isOk());

        // Reading a row is not changing it.
        String profile = "{\"displayName\": \"Viewer\", \"otherUnitIds\": [], \"positionIds\": []}";
        mvc.perform(put("/api/v1/users/" + viewerId).with(csrf()).cookie(viewer).contentType(MediaType.APPLICATION_JSON)
                .content(profile)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityCode\": \"user\", \"action\": \"UPDATE\", \"scope\": \"SELF\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/users/" + viewerId).with(csrf()).cookie(viewer).contentType(MediaType.APPLICATION_JSON)
                .content(profile)).andExpect(status().isOk()).andExpect(jsonPath("$.user.displayName").value("Viewer"));
        mvc.perform(put("/api/v1/users/" + rootId).with(csrf()).cookie(viewer).contentType(MediaType.APPLICATION_JSON)
                .content(profile)).andExpect(status().isNotFound());

        // The built-in administrator sees every row without any data policy.
        assertThat(JsonPath.<Integer>read(body(mvc.perform(get("/api/v1/users").cookie(root))), "$.total")).isEqualTo(2);
    }
}
