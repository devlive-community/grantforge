// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Asking for roles and approving them through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:access-requests",
        "grantforge.setup.token=" + AccessRequestControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class AccessRequestControllerTest
{
    static final String TOKEN = "access-request-token-0123456789";
    private static final String PASSWORD = "a long enough password";
    private static final String NEW_PASSWORD = "another long password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void signIn() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM gf_access_request");
        jdbc.update("DELETE FROM gf_requestable_role");
        admin = login("admin", PASSWORD);
    }

    private Cookie login(String username, String password) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions send(Cookie session, MockHttpServletRequestBuilder request, String body) throws Exception
    {
        return mvc.perform(request.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                .content(body));
    }

    @Test
    void usersAskForRolesThatApproversGrantForAWhile() throws Exception
    {
        String suffix = Long.toString(System.nanoTime() % 100_000);
        String role = JsonPath.read(send(admin, post("/api/v1/roles"), "{\"code\": \"reports-%s\", \"name\": \"报表%s\"}".formatted(suffix, suffix))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        send(admin, put("/api/v1/requestable-roles"), "{\"roles\": [{\"roleId\": \"%s\", \"maxDays\": 30}]}".formatted(role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].maxDays").value(30));
        send(admin, post("/api/v1/users"), """
                {"username": "gary%s", "password": "%s", "profile": {"displayName": "Gary"}}
                """.formatted(suffix, PASSWORD)).andExpect(status().isCreated());
        Cookie gary = login("gary" + suffix, PASSWORD);
        send(gary, post("/api/v1/me/password"), "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me/requestable-roles").cookie(gary))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role.id").value(role))
                .andExpect(jsonPath("$[0].held").value(false));
        send(gary, post("/api/v1/me/access-requests"), "{\"roleId\": \"%s\", \"reason\": \"月底对账\", \"days\": 31}".formatted(role))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-083"));
        String request = JsonPath.read(send(gary, post("/api/v1/me/access-requests"), "{\"roleId\": \"%s\", \"reason\": \"月底对账\", \"days\": 10}"
                .formatted(role))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        // The user cannot see or decide other people's requests.
        mvc.perform(get("/api/v1/access-requests").cookie(gary)).andExpect(status().isForbidden());
        send(gary, post("/api/v1/access-requests/" + request + "/approve"), "{}").andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/access-requests").param("status", "PENDING").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requesterName").value("Gary"))
                .andExpect(jsonPath("$[0].reason").value("月底对账"));
        send(admin, post("/api/v1/access-requests/" + request + "/approve"), "{\"days\": 3, \"comment\": \"仅限本月\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.validUntil").isNotEmpty())
                .andExpect(jsonPath("$.decidedByName").isNotEmpty());
        mvc.perform(get("/api/v1/me/authorization").cookie(gary)).andExpect(jsonPath("$.roles[0]").value("reports-" + suffix));
        mvc.perform(get("/api/v1/me/access-requests").cookie(gary)).andExpect(jsonPath("$[0].comment").value("仅限本月"));

        send(admin, post("/api/v1/access-requests/" + request + "/revoke"), "").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        mvc.perform(get("/api/v1/me/authorization").cookie(gary)).andExpect(jsonPath("$.roles").isEmpty());

        String second = JsonPath.read(send(gary, post("/api/v1/me/access-requests"), "{\"roleId\": \"%s\", \"reason\": \"再申请\", \"days\": 1}"
                .formatted(role)).andReturn().getResponse().getContentAsString(), "$.id");
        send(admin, post("/api/v1/access-requests/" + second + "/reject"), "{\"comment\": \"找主管\"}").andExpect(jsonPath("$.status").value("REJECTED"));
        String third = JsonPath.read(send(gary, post("/api/v1/me/access-requests"), "{\"roleId\": \"%s\", \"reason\": \"第三次\", \"days\": 1}"
                .formatted(role)).andReturn().getResponse().getContentAsString(), "$.id");
        send(gary, post("/api/v1/me/access-requests/" + third + "/cancel"), "").andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/requestable-roles").cookie(admin)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/access-requests").cookie(admin)).andExpect(jsonPath("$.length()").value(3));
    }
}
