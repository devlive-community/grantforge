// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.persistence.tenant.TenantContext;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The open API through the assembled server, with tokens from the authorization server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:open-api",
        "grantforge.setup.token=" + OpenAuthorizationControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class OpenAuthorizationControllerTest
{
    static final String TOKEN = "open-api-token-0123456789abcdef";

    private static final String PASSWORD = "a long enough password";
    private static final String REDIRECT = "https://shop.example/cb";
    private static final String VERIFIER = "dBjftJeZ4CVP-mJ92K9gEcPjXpUbbw6uJIQ0kQ3oA6Wk";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleGrantRepository grants;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private ResourceRepository resources;

    private Cookie root;

    private String clientId = "";

    private String secret = "";

    @BeforeEach
    void registerTheShop() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?", Boolean.class,
                "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "tenantName": "Platform", "username": "root", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        root = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"root\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        String code = "shop-" + System.nanoTime();
        String application = id(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\", \"name\": \"Shop\"}".formatted(code))).andReturn().getResponse().getContentAsString());
        String module = resource(application, "{\"type\": \"MODULE\", \"code\": \"shop\", \"name\": \"Shop\"}");
        String orders = resource(application, "{\"type\": \"PAGE\", \"parentId\": \"%s\", \"code\": \"shop.orders\", \"name\": \"Orders\"}"
                .formatted(module));
        String read = resource(application, "{\"type\": \"API\", \"code\": \"orders.read\", \"name\": \"Read orders\"}");
        resource(application, "{\"type\": \"API\", \"code\": \"orders.delete\", \"name\": \"Delete orders\"}");
        long platform = requireNonNull(jdbc.queryForObject("SELECT tenant_id FROM gf_user_account WHERE username_norm = 'root'", Long.class));
        long account = requireNonNull(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'root'", Long.class));
        TenantContext.runInTenant(platform, () -> {
            long role = roles.save(Role.create(code + "-sellers", "Sellers", null)).requireId();
            grants.save(RoleGrant.create(role, resources.findById(Long.parseLong(orders)).orElseThrow(), GrantEffect.ALLOW, null, account));
            grants.save(RoleGrant.create(role, resources.findById(Long.parseLong(read)).orElseThrow(), GrantEffect.ALLOW, null, account));
            assignments.save(RoleAssignment.create(role, SubjectType.USER, account, RoleAssignment.Terms.UNLIMITED));
        });
        String body = mvc.perform(post("/api/v1/applications/" + application + "/clients").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type": "CONFIDENTIAL", "settings": {"name": "Shop", "redirectUris": ["%s"],
                                 "scopes": ["openid", "permissions"], "grants": ["AUTHORIZATION_CODE", "CLIENT_CREDENTIALS"]}}
                                """.formatted(REDIRECT)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        clientId = JsonPath.read(body, "$.client.clientId");
        secret = JsonPath.read(body, "$.secret");
    }

    @Test
    void answersWhatTheUserMayDoInTheTokensApplicationOnly() throws Exception
    {
        String access = userToken("openid permissions");

        String etag = mvc.perform(get("/api/v1/open/me/authorization").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("root"))
                .andExpect(jsonPath("$.resources").value(contains("shop", "shop.orders")))
                .andExpect(jsonPath("$.permissions").value(contains("orders.read")))
                // The console's own permissions stay out, though root administers the platform.
                .andExpect(jsonPath("$.resources", not(hasItem("system"))))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-cache")))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        mvc.perform(get("/api/v1/open/me/authorization").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .header(HttpHeaders.IF_NONE_MATCH, requireNonNull(etag))).andExpect(status().isNotModified());
        mvc.perform(get("/api/v1/open/me/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .param("permission", "orders.read").param("permission", "orders.delete"))
                .andExpect(jsonPath("$.permissions['orders.read']").value(true))
                .andExpect(jsonPath("$.permissions['orders.delete']").value(false));
        mvc.perform(get("/api/v1/open/me/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .param("permission", IntStream.range(0, 101).mapToObj(Integer::toString).toArray(String[]::new))).andExpect(status().isBadRequest());
    }

    @Test
    void refusesCallsWithoutAWorkingUserTokenWithItsScope() throws Exception
    {
        mvc.perform(get("/api/v1/open/me/authorization")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/open/me/authorization").header(HttpHeaders.AUTHORIZATION, "Bearer forged")).andExpect(status().isUnauthorized());
        // A console session does not reach the open API, and an open API token reaches nothing else.
        mvc.perform(get("/api/v1/open/me/authorization").cookie(root)).andExpect(status().isUnauthorized());
        String access = userToken("openid");
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/open/me/authorization").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GF-SECURITY-004"));
        String machine = JsonPath.read(mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret)).param("grant_type", "client_credentials")
                .param("scope", "permissions")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.access_token");
        mvc.perform(get("/api/v1/open/me/permissions").header(HttpHeaders.AUTHORIZATION, "Bearer " + machine).param("permission", "x"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GF-SECURITY-003"));

        // A revoked token stops working at once, though it has not expired.
        String revoked = userToken("openid permissions");
        mvc.perform(post("/oauth2/revoke").with(httpBasic(clientId, secret)).param("token", revoked)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/open/me/authorization").header(HttpHeaders.AUTHORIZATION, "Bearer " + revoked))
                .andExpect(status().isUnauthorized());
    }

    private String resource(String application, String json) throws Exception
    {
        return id(mvc.perform(post("/api/v1/applications/" + application + "/resources").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString());
    }

    private static String id(String body)
    {
        return JsonPath.read(body, "$.id");
    }

    private String userToken(String scope) throws Exception
    {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        URI authorize = UriComponentsBuilder.fromPath("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", clientId)
                .queryParam("redirect_uri", REDIRECT).queryParam("scope", scope).queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").encode().build().toUri();
        String location = requireNonNull(mvc.perform(get(authorize).cookie(root)).andExpect(status().is3xxRedirection()).andReturn()
                .getResponse().getRedirectedUrl());
        String code = requireNonNull(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code"));
        return JsonPath.read(mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret)).param("grant_type", "authorization_code")
                .param("code", code).param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.access_token");
    }
}
