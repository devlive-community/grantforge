// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health probes and metrics are available without exposing internals. */
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest
{
    @Autowired
    private MockMvc mvc;

    @Test
    void healthAndProbesAreUpWithoutDetails() throws Exception
    {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health/liveness")).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusMetricsCarryTheApplicationTag() throws Exception
    {
        // Metrics need a session unless grantforge.observability.prometheus-public is set.
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(user("operator")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(containsString("application=\"grantforge\"")));
    }

    @Test
    void otherEndpointsAreNotExposed() throws Exception
    {
        mvc.perform(get("/actuator/env").with(user("operator"))).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans").with(user("operator"))).andExpect(status().isNotFound());
    }
}
