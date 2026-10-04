// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.hamcrest.Matchers;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reviewing who holds roles through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:access-reviews",
        "grantforge.setup.token=" + AccessReviewControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class AccessReviewControllerTest
{
    static final String TOKEN = "access-review-token-0123456789";
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
        jdbc.update("DELETE FROM gf_access_review_item");
        jdbc.update("DELETE FROM gf_access_review_round");
        jdbc.update("DELETE FROM gf_access_review_role");
        jdbc.update("DELETE FROM gf_access_review");
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

    private static String read(ResultActions result, String path) throws Exception
    {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    @Test
    void reviewersRevokeAssignmentsThatCompletingTheRoundRemoves() throws Exception
    {
        String suffix = Long.toString(System.nanoTime() % 100_000);
        String role = read(send(admin, post("/api/v1/roles"), "{\"code\": \"ledger-%s\", \"name\": \"总账%s\"}".formatted(suffix, suffix))
                .andExpect(status().isCreated()), "$.id");
        String user = read(send(admin, post("/api/v1/users"), """
                {"username": "rory%s", "password": "%s", "profile": {"displayName": "Rory"}}
                """.formatted(suffix, PASSWORD)).andExpect(status().isCreated()), "$.user.id");
        send(admin, post("/api/v1/roles/" + role + "/assignments"), "{\"subjectType\": \"USER\", \"subjectId\": \"%s\"}".formatted(user))
                .andExpect(status().isCreated());
        Cookie rory = login("rory" + suffix, PASSWORD);
        send(rory, post("/api/v1/me/password"), "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        send(admin, post("/api/v1/access-reviews"), "{\"name\": \"季度复核\", \"roleIds\": [\"%s\"], \"durationDays\": 7, \"intervalDays\": 3}"
                .formatted(role))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-090"));
        String review = read(send(admin, post("/api/v1/access-reviews"), "{\"name\": \"季度复核\", \"roleIds\": [\"%s\"], \"durationDays\": 7}"
                .formatted(role))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unreviewed").value("KEEP"))
                .andExpect(jsonPath("$.roles[0].id").value(role)), "$.id");
        send(admin, put("/api/v1/access-reviews/" + review), "{\"name\": \"季度复核\", \"roleIds\": [\"%s\"], \"durationDays\": 14, \"intervalDays\": 90}"
                .formatted(role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(90));
        String round = read(send(admin, post("/api/v1/access-reviews/" + review + "/start"), "").andExpect(status().isCreated())
                .andExpect(jsonPath("$.progress.total").value(1)), "$.id");
        send(admin, post("/api/v1/access-reviews/" + review + "/start"), "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-091"));

        // The user under review cannot see or decide anything.
        mvc.perform(get("/api/v1/access-reviews").cookie(rory)).andExpect(status().isForbidden());
        send(rory, post("/api/v1/access-review-rounds/" + round + "/decisions"), "{\"itemIds\": [\"1\"], \"decision\": \"KEEP\"}")
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/access-reviews").cookie(admin))
                .andExpect(jsonPath("$[0].openRound.id").value(round))
                .andExpect(jsonPath("$[0].openRound.progress.pending").value(1));
        String item = read(mvc.perform(get("/api/v1/access-review-rounds/" + round + "/items").param("decision", "PENDING").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].subjectName").value("Rory"))
                .andExpect(jsonPath("$.items[0].assigned").value(true)), "$.items[0].id");
        send(admin, post("/api/v1/access-review-rounds/" + round + "/decisions"), "{\"itemIds\": [\"%s\"], \"decision\": \"REVOKE\", \"comment\": \"已调岗\"}"
                .formatted(item))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].decision").value("REVOKE"))
                .andExpect(jsonPath("$[0].comment").value("已调岗"));
        mvc.perform(get("/api/v1/me/authorization").cookie(rory)).andExpect(jsonPath("$.roles[0]").value("ledger-" + suffix));

        send(admin, post("/api/v1/access-review-rounds/" + round + "/complete"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress.revoked").value(1));
        mvc.perform(get("/api/v1/me/authorization").cookie(rory)).andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(get("/api/v1/access-review-rounds/" + round + "/items").cookie(admin))
                .andExpect(jsonPath("$.items[0].outcome").value("REVOKED"))
                .andExpect(jsonPath("$.items[0].assigned").value(false));
        send(admin, post("/api/v1/access-review-rounds/" + round + "/cancel"), "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-AUTHZ-092"));
        mvc.perform(get("/api/v1/access-reviews/" + review + "/rounds").cookie(admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].endedByName").isNotEmpty());

        send(admin, delete("/api/v1/access-reviews/" + review), "").andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/access-reviews").cookie(admin)).andExpect(jsonPath("$").isEmpty());
        send(admin, delete("/api/v1/users/" + user), "").andExpect(status().isNoContent());
        send(admin, delete("/api/v1/roles/" + role), "").andExpect(status().isNoContent());
    }

    @Test
    void administratorsSeeThePageInTheirMenu() throws Exception
    {
        mvc.perform(get("/api/v1/me/authorization").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resources").value(Matchers.hasItems("system.access-review", "system.access-review.btn.manage",
                        "system.access-review.btn.decide")));
    }
}
