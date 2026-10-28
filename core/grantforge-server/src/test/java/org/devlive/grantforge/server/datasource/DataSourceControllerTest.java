// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.datasource;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Independent API access controls, safe configuration output and credential rotation. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:data-source-controller",
        "grantforge.setup.token=data-source-controller-test-setup",
})
@AutoConfigureMockMvc
class DataSourceControllerTest
{
    @Autowired
    private MockMvc mvc;

    @Test
    void administratorsManageDataSourcesWithoutChangingOidcConfiguration() throws Exception
    {
        mvc.perform(get("/api/v1/data-sources")).andExpect(status().isUnauthorized());
        String password = "a long enough test password";
        mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"token":"data-source-controller-test-setup","username":"admin","password":"%s"}
                """.formatted(password))).andExpect(status().isOk());
        Cookie admin = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"%s\"}".formatted(password))).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        String identity = JsonPath.read(mvc.perform(post("/api/v1/identity-sources").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"corp","name":"Internal SSO","type":"OIDC","oidc":{"issuer":"https://sso.example.com","clientId":"client"}}
                """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String body = """
                {"code":"directory","name":"Organization","type":"DINGTALK","identitySourceId":"%s",
                 "settings":{"corpId":"corp","appKey":"app","rootDepartmentId":"1"},"secret":"sdk-write-only"}
                """.formatted(identity);
        String data = JsonPath.read(mvc.perform(post("/api/v1/data-sources").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.loginUpdates").value(true))
                .andExpect(jsonPath("$.secretSet").value(true)).andExpect(jsonPath("$.secret").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get("/api/v1/data-sources").cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].identitySourceName").value("Internal SSO"));
        mvc.perform(put("/api/v1/data-sources/" + data).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(body.replace("sdk-write-only", "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secretSet").value(true));
        mvc.perform(get("/api/v1/identity-sources/" + identity).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.oidc.clientId").value("client")).andExpect(jsonPath("$.oidc.dingtalk").doesNotExist());
        // Authentication sources retain their original lifecycle; deleting an unused OIDC source does not delete organization data.
        mvc.perform(delete("/api/v1/identity-sources/" + identity).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/data-sources").cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].identitySourceName").value(""));
        mvc.perform(put("/api/v1/data-sources/" + data).with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"code\":\"directory\"", "\"enabled\":false,\"code\":\"directory\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(delete("/api/v1/data-sources/" + data).with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/data-sources").cookie(admin)).andExpect(jsonPath("$.length()").value(0));
    }
}
