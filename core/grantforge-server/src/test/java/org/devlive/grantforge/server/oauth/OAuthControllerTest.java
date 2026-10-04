// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Administration of the authorization server through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oauth-admin",
        "grantforge.setup.token=" + OAuthControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class OAuthControllerTest
{
    static final String TOKEN = "oauth-admin-token-0123456789abc";

    private static final String PASSWORD = "a long enough password";

    @Autowired
    private MockMvc mvc;

    @Test
    void describesTheServerAndRotatesItsKeysForPlatformAdministratorsOnly() throws Exception
    {
        mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        Cookie root = login("root", PASSWORD);
        mvc.perform(post("/api/v1/tenants").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON).content("""
                {"code": "acme", "name": "Acme", "adminUsername": "boss", "adminPassword": "%s"}
                """.formatted(PASSWORD))).andExpect(status().isCreated());

        String first = JsonPath.read(mvc.perform(get("/api/v1/oauth").cookie(root))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost"))
                .andExpect(jsonPath("$.discoveryUrl").value("http://localhost/.well-known/openid-configuration"))
                .andExpect(jsonPath("$.keys[0].active").value(true))
                .andExpect(jsonPath("$.keys[0].algorithm").value("RS256"))
                .andReturn().getResponse().getContentAsString(), "$.keys[0].keyId");
        String second = JsonPath.read(mvc.perform(post("/api/v1/oauth/signing-keys/rotate").with(csrf()).cookie(root))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.keyId");
        mvc.perform(get("/api/v1/oauth").cookie(root))
                .andExpect(jsonPath("$.keys[0].keyId").value(second))
                .andExpect(jsonPath("$.keys[1].keyId").value(first))
                .andExpect(jsonPath("$.keys[1].active").value(false))
                .andExpect(jsonPath("$.keys[1].publishedUntil").isString());
        mvc.perform(get("/oauth2/jwks")).andExpect(jsonPath("$.keys[*].kid", hasItem(first)));

        Cookie boss = login("boss", PASSWORD);
        mvc.perform(get("/api/v1/oauth").cookie(boss)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/oauth/signing-keys/rotate").with(csrf()).cookie(boss)).andExpect(status().isForbidden());
    }

    private Cookie login(String username, String password) throws Exception
    {
        return requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
    }
}
