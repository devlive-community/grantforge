// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

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

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Answers and explanations of what accounts may use, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authzinsight",
        "grantforge.setup.token=" + AuthorizationControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class AuthorizationControllerTest
{
    static final String TOKEN = "insight-token-0123456789abcdef";
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
                    Instant.now()).withDisplayName("Viewer")));
        }
    }

    private Cookie login(String username) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions ask(Cookie session, String path, String json) throws Exception
    {
        return mvc.perform(post("/api/v1/authz/" + path).with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(ResultActions result) throws Exception
    {
        return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void answersAndExplainsWhatAccountsMayUse() throws Exception
    {
        Cookie root = login("root");
        String viewer = String.valueOf(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'viewer'", Long.class));
        ask(root, "check", """
                {"checks": [{"kind": "RESOURCE", "code": "system.user"}, {"kind": "PERMISSION", "code": "system.authz.explain"}]}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].allowed").value(true))
                .andExpect(jsonPath("$.results[1].allowed").value(true));
        ask(root, "explain", "{\"accountId\": \"%s\", \"kind\": \"RESOURCE\", \"code\": \"system.user\"}".formatted(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(viewer))
                .andExpect(jsonPath("$.outcome").value("NOT_GRANTED")).andExpect(jsonPath("$.paths.length()").value(0));

        String role = JsonPath.read(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"user-viewers\", \"name\": \"User viewers\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        List<?> consoles = JsonPath.read(body(mvc.perform(get("/api/v1/applications").cookie(root))), "$[?(@.code == 'grantforge-console')].id");
        String console = String.valueOf(consoles.get(0));
        List<?> page = JsonPath.read(body(mvc.perform(get("/api/v1/applications/" + console + "/resources").cookie(root))),
                "$[?(@.code == 'system.user')].id");
        mvc.perform(put("/api/v1/roles/" + role + "/grants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": \"%s\", \"changes\": [{\"resourceId\": \"%s\", \"effect\": \"ALLOW\"}]}"
                        .formatted(console, page.get(0)))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/roles/" + role + "/assignments").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectType\": \"USER\", \"subjectId\": \"%s\"}".formatted(viewer))).andExpect(status().isCreated());

        ask(root, "explain", "{\"accountId\": \"%s\", \"kind\": \"PERMISSION\", \"code\": \"system.user.read\"}".formatted(viewer))
                .andExpect(jsonPath("$.outcome").value("ALLOWED"))
                .andExpect(jsonPath("$.paths[0].roles[0].code").value("user-viewers"))
                .andExpect(jsonPath("$.paths[0].roles[0].assignedTo[0].type").value("USER"))
                .andExpect(jsonPath("$.paths[0].roles[0].assignedTo[0].id").value(viewer))
                .andExpect(jsonPath("$.paths[0].resources[0].code").value("system.user"))
                .andExpect(jsonPath("$.paths[0].resources[0].via").value("GRANT"))
                .andExpect(jsonPath("$.paths[0].resources[1].via").value("DEPENDENCY"));
        ask(root, "check", "{\"accountId\": \"%s\", \"checks\": [{\"kind\": \"PERMISSION\", \"code\": \"system.user.read\"}]}".formatted(viewer))
                .andExpect(jsonPath("$.results[0].allowed").value(true));
        ask(root, "effective", "{\"accountId\": \"%s\"}".formatted(viewer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].code").value("user-viewers"))
                .andExpect(jsonPath("$.roles[0].active").value(true))
                .andExpect(jsonPath("$.resources[?(@.code == 'system.user')].parentCode").exists())
                .andExpect(jsonPath("$.permissions[?(@.code == 'system.user.read')]").exists())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.fields").isEmpty());
        ask(root, "effective", "{}").andExpect(jsonPath("$.data[?(@.entityCode == 'user')].allow[0].scope").exists());
        ask(root, "simulate", "{\"accountId\": \"%s\", \"removeRoles\": [\"%s\"]}".formatted(viewer, role)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rolesBefore[0]").value("user-viewers")).andExpect(jsonPath("$.rolesAfter.length()").value(0))
                .andExpect(jsonPath("$.lostResources[?(@.code == 'system.user')]").exists())
                .andExpect(jsonPath("$.lostPermissions[?(@.code == 'system.user.read')]").exists());
        ask(root, "simulate", "{\"accountId\": \"%s\", \"grants\": [{\"roleId\": \"%s\", \"resourceId\": \"%s\", \"effect\": \"DENY\"}]}"
                .formatted(viewer, role, page.get(0))).andExpect(jsonPath("$.lostResources[?(@.code == 'system.user')]").exists());
        ask(root, "simulate", "{\"addRoles\": [\"x\"]}").andExpect(status().isBadRequest());
    }

    @Test
    void needsItsOwnPermissionsAndWellFormedQuestions() throws Exception
    {
        Cookie root = login("root");
        Cookie viewer = login("viewer");
        String one = "{\"checks\": [{\"kind\": \"RESOURCE\", \"code\": \"system\"}]}";
        ask(viewer, "check", one).andExpect(status().isForbidden());
        ask(viewer, "explain", "{\"kind\": \"RESOURCE\", \"code\": \"system\"}").andExpect(status().isForbidden());
        ask(viewer, "simulate", "{}").andExpect(status().isForbidden());
        ask(root, "check", "{\"accountId\": \"x\", \"checks\": [{\"kind\": \"RESOURCE\", \"code\": \"system\"}]}")
                .andExpect(status().isBadRequest());
        ask(root, "check", "{\"accountId\": \"424242\", \"checks\": [{\"kind\": \"RESOURCE\", \"code\": \"system\"}]}")
                .andExpect(status().isNotFound());
        ask(root, "check", "{\"checks\": []}").andExpect(status().isBadRequest());
        ask(root, "check", "{\"checks\": [" + String.join(", ", Collections.nCopies(101, "{\"kind\": \"RESOURCE\", \"code\": \"system\"}"))
                + "]}").andExpect(status().isBadRequest());
        ask(root, "explain", "{\"kind\": \"RESOURCE\"}").andExpect(status().isBadRequest());
        ask(root, "explain", "{\"kind\": \"PERMISSION\", \"code\": \"system.nope\"}").andExpect(jsonPath("$.outcome").value("UNKNOWN"));
    }
}
