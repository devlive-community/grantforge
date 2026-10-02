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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** How the console issues agent tokens. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agentadmin",
        "grantforge.setup.token=" + AgentAdministrationControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class AgentAdministrationControllerTest
{
    static final String TOKEN = "agentadmin-token-0123456789abcdef";

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
    void refusesTokensWithoutNameOrForUnknownServices() throws Exception
    {
        Cookie root = AgentTestSupport.login(mvc);
        String service = AgentTestSupport.service(mvc, root, "warehouse");
        mvc.perform(post("/api/v1/services/" + service + "/agent-tokens").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content("{\"name\": \" \", \"expiresAt\": \"2001-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'expiresAt')].message").value("过期时间必须晚于现在。"));
        mvc.perform(post("/api/v1/services/424242/agent-tokens").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"a\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/services/" + service + "/agent-tokens").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"b\", \"expiresAt\": \"2999-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token.expiresAt").value("2999-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.secret").exists());
        mvc.perform(get("/api/v1/services/424242/agents").cookie(root)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/agent-tokens/424242/revoke").with(csrf()).cookie(root)).andExpect(status().isNotFound());
    }
}
