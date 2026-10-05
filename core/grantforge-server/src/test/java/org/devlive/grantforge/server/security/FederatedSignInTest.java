// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.identity.application.MfaService;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.testsupport.TotpCodes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in with an OpenID Connect provider through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:federated-sign-in",
        "grantforge.setup.token=" + FederatedSignInTest.TOKEN,
})
@AutoConfigureMockMvc
class FederatedSignInTest
{
    static final String TOKEN = "federated-token-0123456789";
    private static final String PASSWORD = "a long enough password";

    private static FakeProvider provider;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MfaService mfa;

    @Autowired
    private FederatedClients clients;

    @BeforeAll
    static void startProvider() throws Exception
    {
        provider = FakeProvider.start();
    }

    @AfterAll
    static void stopProvider()
    {
        provider.close();
    }

    @BeforeEach
    void addProvider() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM gf_mfa_recovery_code");
        jdbc.update("DELETE FROM gf_mfa_factor");
        jdbc.update("DELETE FROM gf_external_identity");
        jdbc.update("DELETE FROM gf_user_account WHERE system_account = ?", false);
        jdbc.update("DELETE FROM gf_identity_source");
        ((Map<?, ?>) requireNonNull(ReflectionTestUtils.getField(clients, "registrations"))).clear();
        Cookie admin = requireNonNull(mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"admin\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        mvc.perform(post("/api/v1/identity-sources").with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content("""
                        {"code": "okta", "name": "Okta", "type": "OIDC", "secret": "client-secret",
                         "oidc": {"issuer": "%s", "clientId": "grantforge"}}
                        """.formatted(provider.issuer())))
                .andExpect(status().isCreated());
    }

    /** A sign-in under way: the browser's session cookie and the provider's authorization request. */
    private record Started(Cookie session, UriComponents authorization)
    {
        String parameter(String name)
        {
            return URLDecoder.decode(requireNonNull(authorization.getQueryParams().getFirst(name)), StandardCharsets.UTF_8);
        }
    }

    private Started start(String query) throws Exception
    {
        MvcResult started = mvc.perform(get("/api/v1/auth/federated/okta" + query)).andExpect(status().isFound()).andReturn();
        Cookie session = requireNonNull(started.getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        String authorize = requireNonNull(started.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(authorize).isEqualTo("/api/v1/auth/federated/authorize/okta");
        MvcResult redirected = mvc.perform(get(authorize).cookie(session)).andExpect(status().isFound()).andReturn();
        UriComponents authorization = UriComponentsBuilder.fromUriString(requireNonNull(redirected.getResponse().getHeader(HttpHeaders.LOCATION)))
                .build();
        return new Started(session, authorization);
    }

    private MvcResult callback(Started started, String state) throws Exception
    {
        return mvc.perform(get("/api/v1/auth/federated/callback/okta").param("code", "the-code").param("state", state).cookie(started.session()))
                .andExpect(status().isFound()).andReturn();
    }

    @Test
    void signsUsersInWithTheProviderAndGivesThemAnAccount() throws Exception
    {
        Started started = start("?redirect=/admin/users");
        assertThat(started.authorization().toUriString()).startsWith(provider.issuer() + "/authorize");
        assertThat(started.parameter("client_id")).isEqualTo("grantforge");
        assertThat(started.parameter("redirect_uri")).isEqualTo("http://localhost/api/v1/auth/federated/callback/okta");
        assertThat(started.parameter("scope")).isEqualTo("openid profile email");
        assertThat(started.parameter("code_challenge_method")).isEqualTo("S256");
        provider.signIn("sub-frank", "frank", "Frank F", started.parameter("nonce"));

        MvcResult done = callback(started, started.parameter("state"));

        assertThat(done.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/#/admin/users");
        Cookie session = requireNonNull(done.getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        assertThat(session.getValue()).isNotEqualTo(started.session().getValue());
        mvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("frank"))
                .andExpect(jsonPath("$.displayName").value("Frank F"))
                .andExpect(jsonPath("$.identitySource").value("Okta"));
        // The code was redeemed with the secret and the PKCE verifier.
        assertThat(provider.tokenRequests()).last().asString().contains("code=the-code", "code_verifier=", "auth=Basic ");
        mvc.perform(get("/api/v1/bootstrap")).andExpect(jsonPath("$.signInSources[0].code").value("okta"));
    }

    @Test
    void sendsRefusalsBackToTheSignInPage() throws Exception
    {
        mvc.perform(get("/api/v1/auth/federated/nobody"))
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                        .isEqualTo("/#/auth/login?federatedError=GF-IDENTITY-118"));

        // A forged state is refused by Spring Security.
        Started forged = start("");
        assertThat(callback(forged, "forged").getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/#/auth/login?federatedError=GF-IDENTITY-118");

        // A token without the user name claim cannot sign anyone in.
        Started nameless = start("");
        provider.signIn("sub-x", null, "X", nameless.parameter("nonce"));
        assertThat(callback(nameless, nameless.parameter("state")).getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/#/auth/login?federatedError=GF-IDENTITY-118");

        // A local account's name is not taken over.
        Started taken = start("");
        provider.signIn("sub-evil", "admin", "Evil", taken.parameter("nonce"));
        assertThat(callback(taken, taken.parameter("state")).getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/#/auth/login?federatedError=GF-IDENTITY-116");
    }

    @Test
    void accountsWithTwoStepSignInContinueWithTheirSecondFactor() throws Exception
    {
        Started first = start("");
        provider.signIn("sub-gina", "gina", "Gina G", first.parameter("nonce"));
        callback(first, first.parameter("state"));
        Map<String, Object> gina = jdbc.queryForMap("SELECT id, tenant_id FROM gf_user_account WHERE username_norm = 'gina'");
        long accountId = ((Number) requireNonNull(gina.get("id"))).longValue();
        long tenantId = ((Number) requireNonNull(gina.get("tenant_id"))).longValue();
        String secret = TenantContext.callInTenant(tenantId, () -> mfa.enroll(accountId).secret());
        TenantContext.callInTenant(tenantId, () -> mfa.confirm(accountId, TotpCodes.code(secret, Instant.now())));

        // MockMvc encodes the query itself.
        Started second = start("?authorize=/oauth2/authorize?client_id=gf_a");
        provider.signIn("sub-gina", "gina", "Gina G", second.parameter("nonce"));
        MvcResult waiting = callback(second, second.parameter("state"));

        assertThat(waiting.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/#/auth/login?mfa=1&authorize=%2Foauth2%2Fauthorize%3Fclient_id%3Dgf_a");
        Cookie session = requireNonNull(waiting.getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE));
        mvc.perform(get("/api/v1/me").cookie(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/mfa").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(TotpCodes.code(secret, Instant.now().plusSeconds(30)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("gina"));
    }
}
