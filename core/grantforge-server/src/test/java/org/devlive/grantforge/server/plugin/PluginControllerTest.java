// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.plugin;

import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Installed plugins through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plugins",
        "grantforge.setup.token=" + PluginControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class PluginControllerTest
{
    static final String TOKEN = "plugins-token-0123456789abcdef";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String PASSWORD = "a long enough password";

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

    private Cookie login() throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"root\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    @Test
    void listsSwitchesAndLooksUpThePlugins() throws Exception
    {
        Cookie root = login();
        mvc.perform(get("/api/v1/plugins").cookie(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("builtin-demo"))
                .andExpect(jsonPath("$[0].source").value("BUILTIN"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].serviceTypes[0].resources[1]").value("table"))
                .andExpect(jsonPath("$[0].serviceTypes[0].accessTypes[0]").value("select"));

        mvc.perform(post("/api/v1/plugins/builtin-demo/disable").with(csrf()).cookie(root))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.serviceTypes").isEmpty());
        mvc.perform(post("/api/v1/plugins/rescan").with(csrf()).cookie(root))
                .andExpect(jsonPath("$[0].status").value("DISABLED"));
        mvc.perform(post("/api/v1/plugins/builtin-demo/enable").with(csrf()).cookie(root))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/plugins/nothing/enable").with(csrf()).cookie(root)).andExpect(status().isNotFound());
    }

    @Test
    void reportsWhatSwitchingAPluginOffWouldAffect() throws Exception
    {
        Cookie root = login();
        // The console asks for this before it offers to switch the plugin off, and has its id typed to confirm.
        mvc.perform(get("/api/v1/plugins/builtin-demo/impact").cookie(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pluginId").value("builtin-demo"))
                .andExpect(jsonPath("$.serviceTypes[0]").value("demo"))
                .andExpect(jsonPath("$.services").isArray());
        mvc.perform(get("/api/v1/plugins/nothing/impact").cookie(root)).andExpect(status().isNotFound());
    }
}
