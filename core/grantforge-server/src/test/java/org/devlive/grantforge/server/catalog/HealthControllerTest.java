// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The catalog check-up through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-health",
        "grantforge.setup.token=" + HealthControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class HealthControllerTest
{
    static final String TOKEN = "catalog-health-token-0123456789";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private CatalogFlow flow;

    @BeforeEach
    void setUp() throws Exception
    {
        flow = new CatalogFlow(mvc, jdbc, TOKEN);
    }

    @Test
    void theShippedConsoleIsHealthyAndUnknownApplicationsAreReported() throws Exception
    {
        Cookie root = flow.login("root");
        List<?> ids = JsonPath.read(mvc.perform(get("/api/v1/applications").cookie(root)).andReturn().getResponse()
                .getContentAsString(), "$[?(@.code == 'grantforge-console')].id");
        String console = String.valueOf(ids.get(0));

        // Every button of the manifest reaches an API and every API is needed, so the console has nothing to report.
        mvc.perform(get("/api/v1/applications/" + console + "/health").cookie(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(console))
                .andExpect(jsonPath("$.checkedAt").isNotEmpty())
                .andExpect(jsonPath("$.findings").isEmpty());
        mvc.perform(get("/api/v1/applications/1/health").cookie(root)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/applications/x/health").cookie(root)).andExpect(status().isNotFound());
    }
}
