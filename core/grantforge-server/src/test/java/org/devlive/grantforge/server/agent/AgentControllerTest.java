// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An agent's life through the assembled server: token, heartbeats, signed snapshots, revocation. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agents",
        "grantforge.setup.token=" + AgentControllerTest.TOKEN,
        "grantforge.plugins.directory=target/no-plugins-here",
})
@AutoConfigureMockMvc
class AgentControllerTest
{
    static final String TOKEN = "agents-token-0123456789abcdef";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception
    {
        AgentTestSupport.setUp(mvc, jdbc, TOKEN);
    }

    @Test
    void agentsReportAndDownloadSignedSnapshotsUntilTheirTokenIsRevoked() throws Exception
    {
        Cookie root = AgentTestSupport.login(mvc);
        String service = AgentTestSupport.service(mvc, root, "dw");
        mvc.perform(post("/api/v1/services/" + service + "/policies").with(csrf()).cookie(root).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "sales", "labels": [], "document": {
                                  "resources": {"database": {"values": ["sales"], "excludes": false, "recursive": false},
                                                "table": {"values": ["*"], "excludes": false, "recursive": false}},
                                  "allow": [{"users": ["root"], "roles": ["platform-admin"], "groups": ["public"], "accessTypes": ["select"]}]}}
                                """))
                .andExpect(status().isCreated());
        String secret = AgentTestSupport.issue(mvc, root, service);
        String bearer = "Bearer " + secret;

        // No CSRF token: agents are not browsers.
        mvc.perform(post("/api/v1/agent/heartbeat").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instance\": \"hs2-1\", \"host\": \"10.0.0.5\", \"agentVersion\": \"1.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyVersion").value(1))
                .andExpect(jsonPath("$.refreshSeconds").value(30));
        mvc.perform(get("/api/v1/services/" + service + "/agents").cookie(root))
                .andExpect(jsonPath("$[0].instance").value("hs2-1"))
                .andExpect(jsonPath("$[0].status").value("OUTDATED"));

        MockHttpServletResponse snapshot = mvc.perform(get("/api/v1/agent/policies").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(header().string(AgentController.VERSION_HEADER, "1"))
                .andExpect(jsonPath("$.service").value("dw"))
                .andExpect(jsonPath("$.policies[0].name").value("sales"))
                .andExpect(jsonPath("$.roles['platform-admin']", hasItem("root")))
                .andReturn().getResponse();
        String etag = requireNonNull(snapshot.getHeader(HttpHeaders.ETAG));
        String key = mvc.perform(get("/api/v1/agent/signing-key").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.algorithm").value("Ed25519"))
                .andExpect(jsonPath("$.keyId").value(requireNonNull(snapshot.getHeader(AgentController.KEY_HEADER))))
                .andReturn().getResponse().getContentAsString();
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder()
                .decode(JsonPath.<String>read(key, "$.publicKey")))));
        verifier.update(snapshot.getContentAsByteArray());
        assertThat(verifier.verify(Base64.getDecoder().decode(requireNonNull(snapshot.getHeader(AgentController.SIGNATURE_HEADER)))))
                .isTrue();
        mvc.perform(get("/api/v1/agent/policies").header(HttpHeaders.AUTHORIZATION, bearer).header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag));

        mvc.perform(post("/api/v1/agent/heartbeat").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instance\": \"hs2-1\", \"appliedPolicyVersion\": 1}"))
                .andExpect(status().isOk());
        String agents = mvc.perform(get("/api/v1/services/" + service + "/agents").cookie(root))
                .andExpect(jsonPath("$[0].status").value("CURRENT"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/agent/heartbeat").header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instance\": \"has space\"}"))
                .andExpect(status().isBadRequest());

        String tokenId = JsonPath.read(mvc.perform(get("/api/v1/services/" + service + "/agent-tokens").cookie(root))
                .andExpect(jsonPath("$[0].usable").value(true))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc.perform(post("/api/v1/agent-tokens/" + tokenId + "/revoke").with(csrf()).cookie(root)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/agent/policies").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/v1/services/" + service + "/agents/" + JsonPath.read(agents, "$[0].id")).with(csrf()).cookie(root))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/services/" + service + "/agents").cookie(root)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/v1/services/" + service).with(csrf()).cookie(root)).andExpect(status().isNoContent());
    }
}
