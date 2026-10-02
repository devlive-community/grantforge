// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
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

import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tenant administration through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tenant-flow",
        "grantforge.setup.token=" + TenantControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class TenantControllerTest
{
    static final String TOKEN = "tenant-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
    }

    private Cookie login(String username, String password) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions create(Cookie session, String code, String admin) throws Exception
    {
        return mvc.perform(post("/api/v1/tenants").with(csrf()).cookie(session).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"code": "%s", "name": "%s 公司", "adminUsername": "%s", "adminDisplayName": "Boss",
                         "adminPassword": "%s"}
                        """.formatted(code, code, admin, PASSWORD)));
    }

    @Test
    void platformAdministratorsManageTenantsAndTenantAdministratorsCannot() throws Exception
    {
        Cookie root = login("root", PASSWORD);
        mvc.perform(get("/api/v1/me/authorization").cookie(root))
                .andExpect(jsonPath("$.unrestricted").value(true))
                .andExpect(jsonPath("$.roles").value(hasItem("platform-admin")))
                .andExpect(jsonPath("$.resources").value(hasItem("platform.tenant")))
                .andExpect(jsonPath("$.permissions").value(hasItem("platform.tenant.create")));

        String body = create(root, "acme", "acme-boss")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("acme"))
                .andExpect(jsonPath("$.name").value("acme 公司"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.platform").value(false))
                .andExpect(jsonPath("$.accounts").value(1))
                .andReturn().getResponse().getContentAsString();
        String acme = JsonPath.read(body, "$.id");
        create(root, "acme", "other-boss").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-030"))
                .andExpect(jsonPath("$.detail").value("租户编码“acme”已被使用。"));
        create(root, "globex", "ROOT").andExpect(jsonPath("$.code").value("GF-IDENTITY-031"));
        mvc.perform(post("/api/v1/tenants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/tenants").param("q", "ACME").cookie(root))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(acme));
        mvc.perform(get("/api/v1/tenants").cookie(root))
                .andExpect(jsonPath("$.items[0].platform").value(true))
                .andExpect(jsonPath("$.items[0].name").value("Platform"));
        mvc.perform(put("/api/v1/tenants/" + acme).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Acme Group\"}"))
                .andExpect(jsonPath("$.name").value("Acme Group"));
        mvc.perform(get("/api/v1/tenants/" + acme).cookie(root)).andExpect(jsonPath("$.name").value("Acme Group"));
        mvc.perform(get("/api/v1/tenants/not-an-id").cookie(root)).andExpect(status().isNotFound());

        // The tenant's administrator signs in, must change the password, and never sees the tenant list.
        Cookie boss = login("acme-boss", PASSWORD);
        mvc.perform(get("/api/v1/me").cookie(boss)).andExpect(jsonPath("$.passwordChangeRequired").value(true));
        mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(boss).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"%s\", \"newPassword\": \"the boss's own password\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/authorization").cookie(boss))
                .andExpect(jsonPath("$.unrestricted").value(false))
                .andExpect(jsonPath("$.roles").value(hasItem("tenant-admin")))
                .andExpect(jsonPath("$.resources").value(hasItem("system.user")))
                .andExpect(jsonPath("$.resources").value(not(hasItem("platform.tenant"))));
        mvc.perform(get("/api/v1/tenants").cookie(boss)).andExpect(status().isForbidden());

        // Suspending the tenant ends the boss's session and blocks signing in again.
        mvc.perform(post("/api/v1/tenants/" + acme + "/suspend").with(csrf()).cookie(root))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/me").cookie(boss)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"acme-boss\", \"password\": \"the boss's own password\"}"))
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-023"));
        mvc.perform(post("/api/v1/tenants/" + acme + "/activate").with(csrf()).cookie(root))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        login("acme-boss", "the boss's own password");
    }

    @Test
    void thePlatformTenantCannotBeSuspended() throws Exception
    {
        Cookie root = login("root", PASSWORD);
        String body = mvc.perform(get("/api/v1/tenants").cookie(root)).andReturn().getResponse().getContentAsString();
        String platform = JsonPath.read(body, "$.items[0].id");

        mvc.perform(post("/api/v1/tenants/" + platform + "/suspend").with(csrf()).cookie(root))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-032"));
    }
}
