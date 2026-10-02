// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sets the server up for agent tests: the root account, a demo service and a token for its agents. */
final class AgentTestSupport
{
    static final String PASSWORD = "a long enough password";

    private AgentTestSupport()
    {
    }

    static void setUp(MockMvc mvc, JdbcTemplate jdbc, String token) throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                    """.formatted(token, PASSWORD))).andExpect(status().isOk());
        }
    }

    static Cookie login(MockMvc mvc) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"root\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    static String service(MockMvc mvc, Cookie root, String name) throws Exception
    {
        return JsonPath.read(mvc.perform(post("/api/v1/services").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceType\": \"demo\", \"name\": \"%s\", \"label\": \"DW\", \"values\": {\"url\": \"demo://dw\"}}"
                                .formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    static String issue(MockMvc mvc, Cookie root, String service) throws Exception
    {
        return JsonPath.read(mvc.perform(post("/api/v1/services/" + service + "/agent-tokens").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"cluster-a\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.secret");
    }
}
