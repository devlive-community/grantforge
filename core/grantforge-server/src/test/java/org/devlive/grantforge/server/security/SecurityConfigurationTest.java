// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Which endpoints are public, with the metrics endpoint opened for scrapers. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-configuration",
        "grantforge.observability.prometheus-public=true",
})
@AutoConfigureMockMvc
class SecurityConfigurationTest
{
    @Autowired
    private MockMvc mvc;

    @Test
    void publicEndpointsNeedNoSession() throws Exception
    {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/bootstrap")).andExpect(status().isOk());
        // Static console files are public; this test classpath has none, so the answer is 404, not 401.
        mvc.perform(get("/static/missing.png")).andExpect(status().isNotFound());
    }

    @Test
    void everyResponseHandsOutTheCsrfCookieTheConsoleEchoes() throws Exception
    {
        mvc.perform(get("/api/v1/bootstrap"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().value("XSRF-TOKEN", not("")));
    }

    @Test
    void otherApisNeedASession() throws Exception
    {
        mvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }
}
