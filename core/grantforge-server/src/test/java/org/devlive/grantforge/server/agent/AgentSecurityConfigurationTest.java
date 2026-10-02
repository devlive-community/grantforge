// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The agent API admits agent tokens only, and agent tokens admit nothing else. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agentsecurity",
        "grantforge.setup.token=" + AgentSecurityConfigurationTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class AgentSecurityConfigurationTest
{
    static final String TOKEN = "agentsec-token-0123456789abcdef";

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
    void separatesAgentsFromConsoleSessions() throws Exception
    {
        Cookie root = AgentTestSupport.login(mvc);
        mvc.perform(get("/api/v1/agent/signing-key")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").exists());
        mvc.perform(get("/api/v1/agent/signing-key").cookie(root)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/agent/signing-key").header(HttpHeaders.AUTHORIZATION, "Bearer gfa_unknown")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/agent/signing-key").header(HttpHeaders.AUTHORIZATION, "Basic cm9vdDp4")).andExpect(status().isUnauthorized());

        String secret = AgentTestSupport.issue(mvc, root, AgentTestSupport.service(mvc, root, "lake"));
        mvc.perform(get("/api/v1/agent/signing-key").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + secret)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/policy-signing-key").cookie(root)).andExpect(jsonPath("$.algorithm").value("Ed25519"));
    }
}
