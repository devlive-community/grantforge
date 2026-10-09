// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Services through the assembled server, with the test plugin's demo type. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:services",
        "grantforge.setup.token=" + ServiceControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class ServiceControllerTest
{
    static final String TOKEN = "services-token-0123456789abcdef";
    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
    }

    private Cookie login() throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"root\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    @Test
    void managesServicesWithoutEverSendingTheirSecrets() throws Exception
    {
        Cookie root = login();
        mvc.perform(get("/api/v1/service-types").cookie(root))
                .andExpect(jsonPath("$[0].name").value("demo"))
                .andExpect(jsonPath("$[0].configFields[1].type").value("SECRET"))
                .andExpect(jsonPath("$[0].resources[1].parent").value("database"));

        String body = """
                {"serviceType": "demo", "name": "warehouse", "label": "Warehouse", "values": {"url": "demo://dw", "password": "s3cret"}}
                """;
        String created = mvc.perform(post("/api/v1/services").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceTypeLabel").value("Demo"))
                .andExpect(jsonPath("$.values.url").value("demo://dw"))
                .andExpect(jsonPath("$.secretsSet[0]").value("password"))
                .andExpect(content().string(not(containsString("s3cret"))))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(post("/api/v1/services/test").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceType\": \"demo\", \"serviceId\": \"%s\", \"values\": {\"url\": \"demo://dw\"}}".formatted(id)))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(post("/api/v1/services/test").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceType\": \"demo\", \"values\": {\"url\": \"demo://dw\", \"password\": \"no\"}}"))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("wrong password"));
        mvc.perform(put("/api/v1/services/" + id).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN")
                        .content("{\"name\": \"warehouse\", \"label\": \"Warehouse\", \"values\": {\"colour\": \"red\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-SERVICE-003"))
                .andExpect(jsonPath("$.errors[?(@.field == 'url')].message").value("必填。"))
                .andExpect(jsonPath("$.errors[?(@.field == 'colour')].message").value("该服务类型没有这项配置。"));
        mvc.perform(post("/api/v1/services/" + id + "/lookup").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\": \"database\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-SERVICE-004"));
        mvc.perform(post("/api/v1/services/" + id + "/browse").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\": \"database\", \"pageSize\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-SERVICE-004"));
        mvc.perform(post("/api/v1/services/" + id + "/browse").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\": \"database\", \"pageSize\": 501}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/services").cookie(root)).andExpect(jsonPath("$[0].name").value("warehouse"));
        mvc.perform(get("/api/v1/services/" + id).cookie(root)).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(delete("/api/v1/services/" + id).with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/services/" + id).cookie(root)).andExpect(status().isNotFound());
    }
}
