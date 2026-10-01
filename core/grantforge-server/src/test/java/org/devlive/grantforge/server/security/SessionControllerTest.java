// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Listing and ending console sessions through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-flow",
        "grantforge.setup.token=" + SessionControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class SessionControllerTest
{
    static final String TOKEN = "session-flow-token-0123456789";
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
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
            long tenant = requireNonNull(jdbc.queryForObject("SELECT id FROM gf_tenant", Long.class));
            TenantContext.runInTenant(tenant, () -> accounts.save(
                    UserAccount.create("bob", encoder.encode(PASSWORD), Instant.now()).withDisplayName("Bob B")));
        }
        jdbc.update("DELETE FROM gf_console_session");
        jdbc.update("DELETE FROM GF_SESSION");
    }

    private Cookie login(String username, String agent) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, agent)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private String ownCurrentId(Cookie session) throws Exception
    {
        String body = mvc.perform(get("/api/v1/me/sessions").cookie(session)).andReturn().getResponse()
                .getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.current == true)].id");
        return ids.get(0);
    }

    private void signedIn(Cookie session, boolean expected) throws Exception
    {
        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(expected ? status().isOk() : status().isUnauthorized());
    }

    @Test
    void usersSeeAndEndTheirOwnSessions() throws Exception
    {
        Cookie laptop = login("admin", "Laptop");
        Cookie phone = login("admin", "Phone");
        login("bob", "Bob's browser");

        mvc.perform(get("/api/v1/me/sessions").cookie(laptop))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.current == true)].userAgent").value("Laptop"))
                .andExpect(jsonPath("$[?(@.current == false)].userAgent").value("Phone"))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].clientIp").value("127.0.0.1"))
                .andExpect(jsonPath("$[0].id").isString());

        mvc.perform(delete("/api/v1/me/sessions/" + ownCurrentId(phone)).with(csrf()).cookie(laptop))
                .andExpect(status().isNoContent());
        signedIn(phone, false);
        signedIn(laptop, true);

        // Ending the current session signs out.
        mvc.perform(delete("/api/v1/me/sessions/" + ownCurrentId(laptop)).with(csrf()).cookie(laptop))
                .andExpect(status().isNoContent());
        signedIn(laptop, false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gf_console_session", Integer.class)).isOne();
    }

    @Test
    void usersCannotEndOtherPeoplesSessionsAsTheirOwn() throws Exception
    {
        Cookie admin = login("admin", "Laptop");
        Cookie bob = login("bob", "Bob's browser");

        mvc.perform(delete("/api/v1/me/sessions/" + ownCurrentId(admin)).with(csrf()).cookie(bob))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/sessions/not-a-number").with(csrf()).cookie(bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GF-COMMON-404"));
        signedIn(admin, true);
    }

    @Test
    void administratorsSeeAndEndEverySession() throws Exception
    {
        Cookie admin = login("admin", "Laptop");
        Cookie bob = login("bob", "Bob's browser");
        String bobId = ownCurrentId(bob);

        mvc.perform(get("/api/v1/sessions").cookie(bob)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/sessions/" + bobId).with(csrf()).cookie(bob)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sessions").param("size", "1").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].username").value("bob"))
                .andExpect(jsonPath("$.items[0].displayName").value("Bob B"));

        mvc.perform(delete("/api/v1/sessions/" + bobId).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        signedIn(bob, false);
        mvc.perform(delete("/api/v1/sessions/" + bobId).with(csrf()).cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/sessions").cookie(admin)).andExpect(jsonPath("$.total").value(1));

        mvc.perform(delete("/api/v1/sessions/" + ownCurrentId(admin)).with(csrf()).cookie(admin))
                .andExpect(status().isNoContent());
        signedIn(admin, false);
    }

    @Test
    void signingOutRemovesTheSessionFromTheList() throws Exception
    {
        Cookie admin = login("admin", "Laptop");

        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(admin)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gf_console_session", Integer.class)).isZero();
        mvc.perform(get("/api/v1/me/sessions")).andExpect(status().isUnauthorized());
    }
}
