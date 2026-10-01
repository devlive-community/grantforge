// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole server on a real database selected with {@code -Dgrantforge.it.database}: every module's
 * changelog applies and validates, and first-run setup plus a full session lifecycle work, including the
 * session attributes Spring Session stores as binary data and the session list.
 */
@SpringBootTest(properties = "grantforge.setup.token=" + ServerDatabaseIT.TOKEN)
@AutoConfigureMockMvc
class ServerDatabaseIT
{
    static final String TOKEN = "server-database-it-0123456789";
    private static final TestDatabase DATABASE = TestDatabase.fromSystemProperty();
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase()
    {
        DATABASE.close();
    }

    @Test
    void setupSignInAndSignOut() throws Exception
    {
        mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"token": "%s", "tenantName": "权限管理-🔐", "username": "admin", "password": "%s"}
                        """.formatted(TOKEN, PASSWORD)))
                .andExpect(status().isOk());

        Cookie session = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"ADMIN\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));

        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantName").value("权限管理-🔐"));
        mvc.perform(get("/api/v1/tenants").param("q", "权限").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].platform").value(true))
                .andExpect(jsonPath("$.items[0].accounts").value(1));
        mvc.perform(get("/api/v1/sessions").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].current").value(true));
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isUnauthorized());
    }
}
