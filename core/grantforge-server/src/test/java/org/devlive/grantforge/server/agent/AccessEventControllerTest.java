// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Access events from agents to the console, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:accessevents",
        "grantforge.setup.token=" + AccessEventControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class AccessEventControllerTest
{
    static final String TOKEN = "access-token-0123456789abcdef";
    private static final String EVENT = """
            {"eventId": "%s", "occurredAt": "%s", "user": "%s", "clientIp": "10.0.0.9", "resource": "sales.orders",
             "resourceType": "table", "accessType": "select", "action": "SELECT", "outcome": "%s", "policyId": %s, "policyVersion": 1,
             "request": "%s"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception
    {
        AgentTestSupport.setUp(mvc, jdbc, TOKEN);
    }

    @Test
    void agentsReportAccessesTheConsoleReads() throws Exception
    {
        Cookie root = AgentTestSupport.login(mvc);
        String service = AgentTestSupport.service(mvc, root, "dw");
        String policy = JsonPath.read(mvc.perform(post("/api/v1/services/" + service + "/policies").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name": "sales", "labels": [], "document": {
                                  "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false},
                                                "table": {"values": ["*"], "excludes": false, "recursive": false}},
                                  "allow": [{"users": ["root"], "accessTypes": ["select"]}]}}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String bearer = "Bearer " + AgentTestSupport.issue(mvc, root, service);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String batch = "{\"instance\": \"hs2-1\", \"events\": [" + String.join(",",
                EVENT.formatted("e1", now.minusSeconds(2), "root", "ALLOWED", "\"" + policy + "\"", "select 1"),
                EVENT.formatted("e2", now.minusSeconds(1), "eve", "DENIED", "null", "x".repeat(1200)),
                EVENT.formatted("e3", now, "root", "ALLOWED", "\"" + policy + "\"", "")) + "]}";

        mvc.perform(post("/api/v1/agent/access-events").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(3));
        mvc.perform(post("/api/v1/agent/access-events").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(batch))
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.duplicates").value(3));
        mvc.perform(post("/api/v1/agent/access-events").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instance\": \"hs2-1\", \"events\": [{\"eventId\": \"bad\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/agent/access-events").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instance\": \"hs2-1\", \"events\": [" + EVENT.formatted("e9", now, "root", "ALLOWED", "\"p1\"", "") + "]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/agent/access-events").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isUnauthorized());

        String first = mvc.perform(get("/api/v1/services/" + service + "/access-events?limit=2").cookie(root))
                .andExpect(jsonPath("$.events[0].eventId").value("e3"))
                .andExpect(jsonPath("$.events[0].policyName").value("sales"))
                .andExpect(jsonPath("$.events[0].enforcer").value("GRANTFORGE"))
                .andExpect(jsonPath("$.events[0].request").doesNotExist())
                .andExpect(jsonPath("$.events[1].eventId").value("e2"))
                .andExpect(jsonPath("$.events[1].enforcer").value("NATIVE"))
                .andExpect(jsonPath("$.events[1].request").value("x".repeat(1000)))
                .andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(first, "$.next");
        mvc.perform(get("/api/v1/services/" + service + "/access-events?limit=2&cursor=" + next).cookie(root))
                .andExpect(jsonPath("$.events[0].eventId").value("e1"))
                .andExpect(jsonPath("$.next").doesNotExist());
        mvc.perform(get("/api/v1/services/" + service + "/access-events?outcome=DENIED&user=EV&from=" + now.minusSeconds(60)
                        + "&until=" + now.plusSeconds(60)).cookie(root))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].user").value("eve"));
        mvc.perform(get("/api/v1/services/" + service + "/access-events?cursor=bogus").cookie(root)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/services/" + service).with(csrf()).cookie(root)).andExpect(status().isNoContent());
    }
}
