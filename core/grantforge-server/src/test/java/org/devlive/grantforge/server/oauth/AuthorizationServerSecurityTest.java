// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The authorization server through the assembled server: sign-in, code with PKCE, tokens, refresh, replay and revocation. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:oauth-flow",
        "grantforge.setup.token=" + AuthorizationServerSecurityTest.TOKEN,
        "grantforge.oauth.issuer=http://localhost",
})
@AutoConfigureMockMvc
class AuthorizationServerSecurityTest
{
    static final String TOKEN = "oauth-flow-token-0123456789abcd";

    private static final String PASSWORD = "a long enough password";
    private static final String REDIRECT = "https://app.example/cb";
    private static final String VERIFIER = "dBjftJeZ4CVP-mJ92K9gEcPjXpUbbw6uJIQ0kQ3oA6Wk";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie root;

    @BeforeEach
    void signIn() throws Exception
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
    }

    @Test
    void signsUsersInWithCodeAndPkceAndRotatesRefreshTokens() throws Exception
    {
        Values client = register("crm-flow", "CONFIDENTIAL");
        String clientId = client.get("clientId");
        String secret = client.get("secret");

        // Not signed in: the browser goes to the console's sign-in page, which brings it back.
        String signIn = mvc.perform(authorize(clientId).accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(signIn).startsWith("/#/auth/login?authorize=%2Foauth2%2Fauthorize%3Fresponse_type%3Dcode");

        Values tokens = exchange(clientId, secret, code(clientId));
        assertThat(tokens.get("scope")).contains("openid");
        mvc.perform(get("/userinfo").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("access_token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferred_username").value("root"))
                .andExpect(jsonPath("$.name").value("root"))
                .andExpect(jsonPath("$.tid").isString());
        // The ID token is signed with a published key and names the nonce.
        String[] parts = tokens.get("id_token").split("\\.");
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String kid = JsonPath.read(header, "$.kid");
        mvc.perform(get("/oauth2/jwks")).andExpect(jsonPath("$.keys[*].kid", hasItem(kid)))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
        assertThat((String) JsonPath.read(payload, "$.nonce")).isEqualTo("n1");
        assertThat((String) JsonPath.read(payload, "$.preferred_username")).isEqualTo("root");

        Values refreshed = refresh(clientId, secret, tokens.get("refresh_token")).andExpect(status().isOk()).andReturnMap();
        assertThat(refreshed.get("refresh_token")).isNotEqualTo(tokens.get("refresh_token"));
        // The replaced token again: the whole authorization is revoked, the new refresh token with it.
        refresh(clientId, secret, tokens.get("refresh_token")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
        refresh(clientId, secret, refreshed.get("refresh_token")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/audit-events").cookie(root).param("action", "OAUTH_TOKEN_REPLAYED"))
                .andExpect(jsonPath("$.events[0].targetId").value(clientId));
    }

    @Test
    void revokesTokensAndRefusesWrongSecretsAndRequestsWithoutPkce() throws Exception
    {
        Values client = register("crm-revoke", "CONFIDENTIAL");
        String clientId = client.get("clientId");
        String secret = client.get("secret");
        Values tokens = exchange(clientId, secret, code(clientId));

        mvc.perform(post("/oauth2/revoke").with(httpBasic(clientId, secret)).param("token", tokens.get("refresh_token")))
                .andExpect(status().isOk());
        refresh(clientId, secret, tokens.get("refresh_token")).andExpect(status().isBadRequest());
        mvc.perform(post("/oauth2/token").with(httpBasic(clientId, "wrong")).param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        // PKCE is required of every client.
        String refused = mvc.perform(get(UriComponentsBuilder.fromPath("/oauth2/authorize").queryParam("response_type", "code")
                        .queryParam("client_id", clientId).queryParam("redirect_uri", REDIRECT).queryParam("scope", "openid").encode().build()
                        .toUri()).cookie(root))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(refused).startsWith(REDIRECT).contains("error=invalid_request");
    }

    @Test
    void rotatedSecretsKeepWorkingForTheGracePeriodAndDisabledClientsLoseTheirTokens() throws Exception
    {
        Values client = register("crm-rotate", "CONFIDENTIAL");
        String clientId = client.get("clientId");
        String old = client.get("secret");
        Values tokens = exchange(clientId, old, code(clientId));
        String fresh = JsonPath.read(mvc.perform(post("/api/v1/clients/" + client.get("id") + "/rotate-secret").with(csrf()).cookie(root)
                .contentType(MediaType.APPLICATION_JSON).content("{\"graceHours\": 1}")).andReturn().getResponse().getContentAsString(),
                "$.secret");

        refresh(clientId, old, tokens.get("refresh_token")).andExpect(status().isOk());
        Values again = exchange(clientId, fresh, code(clientId));
        mvc.perform(put("/api/v1/clients/" + client.get("id")).with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"CRM\", \"redirectUris\": [\"" + REDIRECT + "\"], \"scopes\": [\"openid\", \"profile\"],"
                        + " \"grants\": [\"AUTHORIZATION_CODE\", \"REFRESH_TOKEN\"], \"enabled\": false}"))
                .andExpect(status().isOk());
        refresh(clientId, fresh, again.get("refresh_token")).andExpect(status().isUnauthorized());
    }

    @Test
    void publishesDiscovery() throws Exception
    {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost/oauth2/authorize"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost/oauth2/jwks"))
                .andExpect(jsonPath("$.code_challenge_methods_supported", hasItem("S256")))
                .andExpect(jsonPath("$.id_token_signing_alg_values_supported", hasItem("RS256")));
    }

    private Values register(String code, String type) throws Exception
    {
        String application = JsonPath.read(mvc.perform(post("/api/v1/applications").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"%s\", \"name\": \"CRM\"}".formatted(code)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String body = mvc.perform(post("/api/v1/applications/" + application + "/clients").with(csrf()).cookie(root)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"type": "%s", "settings": {"name": "CRM", "redirectUris": ["%s"], "scopes": ["openid", "profile"],
                                 "grants": ["AUTHORIZATION_CODE", "REFRESH_TOKEN", "CLIENT_CREDENTIALS"]}}
                                """.formatted(type, REDIRECT)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Values(Map.of("id", JsonPath.read(body, "$.client.id"), "clientId", JsonPath.read(body, "$.client.clientId"),
                "secret", JsonPath.read(body, "$.secret")));
    }

    private MockHttpServletRequestBuilder authorize(String clientId) throws Exception
    {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        // The authorization endpoint reads its parameters from the query string, as browsers send them.
        return get(UriComponentsBuilder.fromPath("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", clientId)
                .queryParam("redirect_uri", REDIRECT).queryParam("scope", "openid profile").queryParam("state", "s1").queryParam("nonce", "n1")
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256").encode().build().toUri());
    }

    private String code(String clientId) throws Exception
    {
        String location = mvc.perform(authorize(clientId).cookie(root)).andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(REDIRECT + "?code=")))
                .andReturn().getResponse().getRedirectedUrl();
        Map<String, String> query = UriComponentsBuilder.fromUri(URI.create(requireNonNull(location))).build().getQueryParams().toSingleValueMap();
        assertThat(query).containsEntry("state", "s1");
        return requireNonNull(query.get("code"));
    }

    private Values exchange(String clientId, String secret, String code) throws Exception
    {
        return new TokenCall(mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret)).param("grant_type", "authorization_code")
                .param("code", code).param("redirect_uri", REDIRECT).param("code_verifier", VERIFIER))).andExpect(status().isOk()).andReturnMap();
    }

    private TokenCall refresh(String clientId, String secret, String refreshToken) throws Exception
    {
        return new TokenCall(mvc.perform(post("/oauth2/token").with(httpBasic(clientId, secret)).param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)));
    }

    /** A token endpoint answer. */
    private record TokenCall(ResultActions result)
    {
        TokenCall andExpect(ResultMatcher matcher) throws Exception
        {
            result.andExpect(matcher);
            return this;
        }

        Values andReturnMap() throws Exception
        {
            Map<String, Object> body = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$");
            Map<String, String> values = new HashMap<>();
            body.forEach((name, value) -> values.put(name, String.valueOf(value)));
            return new Values(values);
        }
    }

    /** Answers of the server by name; asking for one it did not give fails the test. */
    private record Values(Map<String, String> values)
    {
        String get(String name)
        {
            return requireNonNull(values.get(name), name);
        }
    }
}
