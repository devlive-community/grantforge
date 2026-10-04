// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.devlive.grantforge.authz.domain.Application;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.devlive.grantforge.server.oauth.ClientOrigins;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The open API's filter chain: bearer tokens only, no session, no CSRF. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:open-api-security")
@AutoConfigureMockMvc
class OpenApiSecurityTest
{
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private OAuthClientRepository clients;

    @Autowired
    private ClientOrigins origins;

    @Test
    void refusesCallsWithoutATokenAsProblemsAndKeepsNoSession() throws Exception
    {
        mvc.perform(get("/api/v1/open/me/authorization"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        // No CSRF token is asked for: what refuses the call is the missing token.
        mvc.perform(post("/api/v1/open/me/authorization")).andExpect(status().isUnauthorized());
    }

    @Test
    void letsBrowsersOfRegisteredClientsCallFromTheirOrigin() throws Exception
    {
        long shop = applications.save(Application.create("cors-shop", "Shop", null)).requireId();
        OAuthClient spa = OAuthClient.create(shop, "gf_cors", ClientType.PUBLIC, null, Instant.now());
        spa.configure("Shop", List.of("https://shop.example/callback"), Set.of("openid", "permissions"), Set.of(ClientGrant.AUTHORIZATION_CODE),
                Duration.ofMinutes(15), Duration.ofDays(1), true);
        clients.save(spa);
        // The origins are read once a minute; another test of this context may have read them before the client existed.
        ((AtomicReference<?>) requireNonNull(ReflectionTestUtils.getField(origins, "known"))).set(null);

        for (String path : List.of("/api/v1/open/me/authorization", "/oauth2/token")) {
            mvc.perform(options(path).header(HttpHeaders.ORIGIN, "https://shop.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, path.startsWith("/oauth2") ? "POST" : "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://shop.example"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
            // Another origin gets no permission, so the browser refuses to send the call.
            mvc.perform(options(path).header(HttpHeaders.ORIGIN, "https://evil.example")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
        mvc.perform(get("/api/v1/open/me/authorization").header(HttpHeaders.ORIGIN, "https://shop.example"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://shop.example"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.ETAG));
    }
}
