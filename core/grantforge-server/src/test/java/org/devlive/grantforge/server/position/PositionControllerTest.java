// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

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

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Positions and their assignment through the user administration, through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:position-flow",
        "grantforge.setup.token=" + PositionControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class PositionControllerTest
{
    static final String TOKEN = "position-flow-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM gf_account_position");
        jdbc.update("DELETE FROM gf_position");
        jdbc.update("DELETE FROM gf_user_account WHERE system_account = ?", false);
        admin = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private String create(String code, String name, int order) throws Exception
    {
        return JsonPath.read(mvc.perform(post("/api/v1/positions").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\", \"name\": \"%s\", \"sortOrder\": %d}".formatted(code, name, order)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void administratorsMaintainPositionsAndAssignThemToAccounts() throws Exception
    {
        String cfo = create("cfo", "财务总监", 2);
        String dev = create("dev", "Developer", 1);
        mvc.perform(post("/api/v1/positions").with(csrf()).cookie(admin).header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"CFO\", \"name\": \"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("岗位编码“cfo”已被使用。"));
        mvc.perform(post("/api/v1/positions").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"x\", \"name\": \"X\", \"sortOrder\": -1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/positions/options").cookie(admin))
                .andExpect(jsonPath("$[0].id").value(dev))
                .andExpect(jsonPath("$[1].name").value("财务总监"));

        String user = JsonPath.read(mvc.perform(post("/api/v1/users").with(csrf()).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"username": "gina", "password": "%s", "profile": {"positionIds": ["%s", "%s"]}}
                                """.formatted(PASSWORD, cfo, dev)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[0].name").value("Developer"))
                .andReturn().getResponse().getContentAsString(), "$.user.id");

        mvc.perform(get("/api/v1/positions").param("q", "财务").cookie(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].holders").value(1));
        mvc.perform(get("/api/v1/positions/" + cfo + "/holders").cookie(admin))
                .andExpect(jsonPath("$.items[0].accountId").value(user));
        mvc.perform(put("/api/v1/positions/" + cfo).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"cfo\", \"name\": \"CFO\", \"description\": \"管钱\"}"))
                .andExpect(jsonPath("$.description").value("管钱"))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.holders").value(1));

        mvc.perform(delete("/api/v1/positions/" + cfo).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/" + user).cookie(admin))
                .andExpect(jsonPath("$.positions.length()").value(1));
        mvc.perform(get("/api/v1/positions/" + cfo + "/holders").cookie(admin)).andExpect(status().isNotFound());
    }
}
