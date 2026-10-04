// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.sdk.DataAction;
import org.devlive.grantforge.sdk.DataEntityRegistrar;
import org.devlive.grantforge.sdk.DataScope;
import org.devlive.grantforge.sdk.GrantForgeClient;
import org.devlive.grantforge.sdk.UserDataAccess;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Data permissions of applications through the assembled server, with the Java SDK on the application's side. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:open-data",
        "grantforge.setup.token=" + OpenDataControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class OpenDataControllerTest
{
    static final String TOKEN = "open-data-token-0123456789abcdef";

    private static final String PASSWORD = "a long enough password";
    private static final String REDIRECT = "https://shop.example/cb";
    private static final String VERIFIER = "dBjftJeZ4CVP-mJ92K9gEcPjXpUbbw6uJIQ0kQ3oA6Wk";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RoleAssignmentRepository assignments;

    @LocalServerPort
    private int port;

    private Cookie root;

    private String application = "";

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
        application = "shop" + System.nanoTime() % 100_000;
        String id = JsonPath.read(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\", \"name\": \"Shop\"}".formatted(application))).andReturn().getResponse().getContentAsString(), "$.id");
        String body = mvc.perform(post("/api/v1/applications/" + id + "/clients").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type": "CONFIDENTIAL", "settings": {"name": "Shop", "redirectUris": ["%s"],
                                 "scopes": ["openid", "permissions", "catalog"], "grants": ["AUTHORIZATION_CODE", "CLIENT_CREDENTIALS"]}}
                                """.formatted(REDIRECT)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        clientId = JsonPath.read(body, "$.client.clientId");
        secret = JsonPath.read(body, "$.secret");
    }

    @Test
    void theShopDeclaresItsEntitiesAndReadsWhatPoliciesSayAboutThem() throws Exception
    {
        RestClient http = RestClient.create("http://localhost:" + port);
        assertThat(new DataEntityRegistrar(http, clientId, secret).declare(List.of(ShopOrder.class))).isTrue();
        String entity = application + ":order";
        mvc.perform(get("/api/v1/data-entities").cookie(root))
                .andExpect(jsonPath("$.entities[?(@.code == '" + entity + "')].previewable").value(false))
                .andExpect(jsonPath("$.entities[?(@.code == '" + entity + "')].fields[0].choices[1]").value("PAID"));

        String role = JsonPath.read(mvc.perform(post("/api/v1/roles").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s-buyers\", \"name\": \"Buyers\"}".formatted(application))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityCode\": \"%s\", \"action\": \"READ\", \"scope\": \"SELF\"}".formatted(entity))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/roles/" + role + "/data-policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"entityCode\": \"%s\", \"action\": \"READ\", \"scope\": \"CONDITION\", \"effect\": \"DENY\","
                        .formatted(entity) + " \"condition\": {\"field\": \"status\", \"op\": \"eq\", \"value\": \"PAID\"}}"))
                .andExpect(status().isCreated());
        long platform = requireNonNull(jdbc.queryForObject("SELECT tenant_id FROM gf_user_account WHERE username_norm = 'root'", Long.class));
        long account = requireNonNull(jdbc.queryForObject("SELECT id FROM gf_user_account WHERE username_norm = 'root'", Long.class));
        TenantContext.runInTenant(platform, () -> assignments.save(RoleAssignment.create(Long.parseLong(role), SubjectType.USER, account,
                RoleAssignment.Terms.UNLIMITED)));

        String access = userToken();
        UserDataAccess rules = new GrantForgeClient(http, Duration.ZERO, 10, Clock.systemUTC()).dataAccess(access);
        assertThat(rules.subject().accountId()).isEqualTo(Long.toString(account));
        UserDataAccess.EntityRules read = rules.rules("order", DataAction.READ).orElseThrow();
        assertThat(read.allow()).extracting(UserDataAccess.Rule::scope).containsExactly(DataScope.SELF);
        assertThat(read.deny()).singleElement().satisfies(rule -> assertThat(requireNonNull(rule.condition()).path("value").asString())
                .isEqualTo("PAID"));
        assertThat(rules.rules("order", DataAction.UPDATE)).isEmpty();
        String etag = mvc.perform(get("/api/v1/open/me/data-access").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        mvc.perform(get("/api/v1/open/me/data-access").header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .header(HttpHeaders.IF_NONE_MATCH, requireNonNull(etag))).andExpect(status().isNotModified());
    }

    @Test
    void refusesDeclarationsWithoutTheClientsOwnCatalogTokenAndWrongOnes() throws Exception
    {
        String user = userToken();
        mvc.perform(put("/api/v1/open/catalog/data-entities").header(HttpHeaders.AUTHORIZATION, "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entities\": []}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GF-SECURITY-005"));
        String withoutCatalog = clientToken("permissions");
        mvc.perform(put("/api/v1/open/catalog/data-entities").header(HttpHeaders.AUTHORIZATION, "Bearer " + withoutCatalog)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entities\": []}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GF-SECURITY-004"));
        String catalog = clientToken("catalog");
        mvc.perform(put("/api/v1/open/catalog/data-entities").header(HttpHeaders.AUTHORIZATION, "Bearer " + catalog)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"entities": [{"code": "Order", "name": "Orders", "fields": [{"code": "s", "name": "S", "type": "CHOICE"}]}]}
                                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GF-AUTHZ-052"))
                .andExpect(jsonPath("$.errors[0].field").value("entities[0].code"))
                .andExpect(jsonPath("$.errors[1].field").value("entities[0].fields[0].choices"));
        mvc.perform(put("/api/v1/open/catalog/data-entities").header(HttpHeaders.AUTHORIZATION, "Bearer " + catalog)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/open/me/data-access").header(HttpHeaders.AUTHORIZATION, "Bearer " + catalog))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GF-SECURITY-003"));
    }

    private String clientToken(String scope) throws Exception
    {
        return JsonPath.read(mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret)).param("grant_type", "client_credentials")
                .param("scope", scope)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.access_token");
    }

    private String userToken() throws Exception
    {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        URI authorize = UriComponentsBuilder.fromPath("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", clientId)
                .queryParam("redirect_uri", REDIRECT).queryParam("scope", "openid permissions").queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").encode().build().toUri();
        String location = requireNonNull(mvc.perform(get(authorize).cookie(root)).andExpect(status().is3xxRedirection()).andReturn()
                .getResponse().getRedirectedUrl());
        String code = requireNonNull(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code"));
        return JsonPath.read(mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret)).param("grant_type", "authorization_code")
                .param("code", code).param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.access_token");
    }
}
