// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import com.jayway.jsonpath.JsonPath;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import jakarta.servlet.http.Cookie;
import org.devlive.grantforge.server.security.SecurityConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static java.util.Objects.requireNonNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Identity sources and directory sign-in through the assembled server. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-sources",
        "grantforge.setup.token=" + IdentitySourceControllerTest.TOKEN,
})
@AutoConfigureMockMvc
class IdentitySourceControllerTest
{
    static final String TOKEN = "identity-source-token-0123456789";
    private static final String PASSWORD = "a long enough password";
    private static final String BASE = "ou=people,dc=example,dc=com";

    private static InMemoryDirectoryServer directory;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeAll
    static void startDirectory() throws Exception
    {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0));
        config.addAdditionalBindCredentials("cn=reader,dc=example,dc=com", "reader-secret");
        directory = new InMemoryDirectoryServer(config);
        directory.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example");
        directory.add("dn: " + BASE, "objectClass: top", "objectClass: organizationalUnit", "ou: people");
        directory.add("dn: uid=erin," + BASE, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson", "uid: erin", "cn: Erin E", "sn: E", "mail: erin@example.com", "userPassword: erin-secret");
        directory.startListening();
    }

    @AfterAll
    static void stopDirectory()
    {
        directory.shutDown(true);
    }

    @BeforeEach
    void signIn() throws Exception
    {
        Boolean required = jdbc.queryForObject("SELECT COUNT(*) = 0 FROM gf_platform_setting WHERE setting_key = ?",
                Boolean.class, "setup.completed-at");
        if (Boolean.TRUE.equals(required)) {
            mvc.perform(post("/api/v1/setup").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                    {"token": "%s", "username": "admin", "password": "%s"}
                    """.formatted(TOKEN, PASSWORD))).andExpect(status().isOk());
        }
        jdbc.update("DELETE FROM gf_external_identity");
        jdbc.update("DELETE FROM gf_user_account WHERE system_account = ?", false);
        jdbc.update("DELETE FROM gf_identity_source");
        admin = requireNonNull(login("admin", PASSWORD).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie(SecurityConfiguration.SESSION_COOKIE));
    }

    private ResultActions login(String username, String password) throws Exception
    {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String body) throws Exception
    {
        return mvc.perform(request.with(csrf()).cookie(admin).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String ldap(String secret)
    {
        return """
                {"code": "corp", "name": "Corporate LDAP", "type": "LDAP", "secret": "%s",
                 "ldap": {"url": "ldap://localhost:%d", "baseDn": "%s", "bindDn": "cn=reader,dc=example,dc=com"}}
                """.formatted(secret, directory.getListenPort(), BASE);
    }

    @Test
    void addsADirectoryWhoseUsersThenSignIn() throws Exception
    {
        String created = send(post("/api/v1/identity-sources"), ldap("reader-secret"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("corp"))
                .andExpect(jsonPath("$.secretSet").value(true))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.ldap.userFilter").value("(&(objectClass=person)(uid={0}))"))
                .andExpect(jsonPath("$.callbackPath").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(post("/api/v1/identity-sources/" + id + "/test").with(csrf()).cookie(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/identity-sources").cookie(admin)).andExpect(jsonPath("$[0].name").value("Corporate LDAP"));

        // The directory's user signs in with the directory's password and gets an account.
        Cookie erin = login("erin", "erin-secret")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Erin E"))
                .andExpect(jsonPath("$.identitySource").value("Corporate LDAP"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false))
                .andReturn().getResponse().getCookie(SecurityConfiguration.SESSION_COOKIE);
        login("erin", "wrong").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("GF-IDENTITY-020"));
        mvc.perform(post("/api/v1/me/password").with(csrf()).cookie(requireNonNull(erin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"erin-secret\", \"newPassword\": \"another long password\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-115"));

        mvc.perform(post("/api/v1/identity-sources/" + id + "/sync").with(csrf()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(1))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.summary").value("found 1, created 0, updated 0, disabled 0"));
        mvc.perform(get("/api/v1/identity-sources/" + id).cookie(admin)).andExpect(jsonPath("$.accounts").value(1));
        mvc.perform(delete("/api/v1/identity-sources/" + id).with(csrf()).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-113"));
    }

    @Test
    void reportsSettingsThatCannotWork() throws Exception
    {
        send(post("/api/v1/identity-sources"), "{\"code\": \"corp\", \"name\": \"x\", \"type\": \"LDAP\", \"ldap\": {\"url\": \"http://x\", \"baseDn\": \"dc=x\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-111"));
        send(post("/api/v1/identity-sources"), "{\"code\": \"corp\", \"type\": \"LDAP\"}").andExpect(status().isBadRequest());
        String id = JsonPath.read(send(post("/api/v1/identity-sources"), ldap("wrong")).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/v1/identity-sources/" + id + "/test").with(csrf()).cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("GF-IDENTITY-112"));
        send(put("/api/v1/identity-sources/" + id), ldap("reader-secret").replace("Corporate LDAP", "Head office"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Head office"));
        mvc.perform(delete("/api/v1/identity-sources/" + id).with(csrf()).cookie(admin)).andExpect(status().isNoContent());

        send(post("/api/v1/identity-sources"), """
                {"code": "okta", "name": "Okta", "type": "OIDC", "secret": "s", "oidc": {"issuer": "https://login.example.com", "clientId": "c"}}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.oidc.scopes").value("openid profile email"))
                .andExpect(jsonPath("$.callbackPath").value("/api/v1/auth/federated/callback/okta"));
    }
}
