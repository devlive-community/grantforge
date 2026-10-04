// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.audit;

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

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The audit log through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditlog",
        "grantforge.setup.token=" + AuditEventControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class AuditEventControllerTest
{
    static final String TOKEN = "audit-token-0123456789abcdef";
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
            TenantContext.runInTenant(platform, () -> accounts.save(UserAccount.create("nobody", encoder.encode(PASSWORD), Instant.now())));
        }
    }

    private Cookie login(String username) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    @Test
    void searchesPagesAndExportsTheAuditLog() throws Exception
    {
        Cookie root = login("root");
        login("root");
        mvc.perform(get("/api/v1/audit-events").param("action", "LOGIN_SUCCEEDED").cookie(root)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].action", everyItem(is("LOGIN_SUCCEEDED"))))
                .andExpect(jsonPath("$.events[0].actorName").value("root"));
        String first = mvc.perform(get("/api/v1/audit-events").param("action", "LOGIN_SUCCEEDED").param("limit", "1").cookie(root))
                .andExpect(jsonPath("$.events.length()").value(1)).andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(first, "$.next");
        mvc.perform(get("/api/v1/audit-events").param("action", "LOGIN_SUCCEEDED").param("limit", "1").param("cursor", next).cookie(root))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].id").value(not(JsonPath.<String>read(first, "$.events[0].id"))));
        mvc.perform(get("/api/v1/audit-events").param("cursor", "garbage").cookie(root)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-events").param("action", "NOPE").cookie(root)).andExpect(status().isBadRequest());

        String csv = mvc.perform(get("/api/v1/audit-events/export").param("action", "LOGIN_SUCCEEDED").cookie(root)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("occurredAt,action,outcome").contains("LOGIN_SUCCEEDED,SUCCESS");

        Cookie nobody = login("nobody");
        mvc.perform(get("/api/v1/audit-events").cookie(nobody)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-events/export").cookie(nobody)).andExpect(status().isForbidden());
        // The refusals were recorded, in the background.
        for (int i = 0; i < 100 && Boolean.FALSE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) >= 2 FROM gf_audit_event WHERE action = 'ACCESS_DENIED'", Boolean.class)); i++) {
            Thread.sleep(50);
        }
        mvc.perform(get("/api/v1/audit-events").param("action", "ACCESS_DENIED").cookie(root))
                .andExpect(jsonPath("$.events[0].actorName").value("nobody"))
                .andExpect(jsonPath("$.events[*].targetId", hasItems("system.audit.read", "system.audit.export")));
    }
}
