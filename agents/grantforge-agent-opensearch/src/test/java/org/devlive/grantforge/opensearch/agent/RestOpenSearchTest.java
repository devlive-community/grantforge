// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestOpenSearchTest
{
    @TempDir
    static Path files;

    private static TestCertificates certificates;

    @BeforeAll
    static void certificates() throws Exception
    {
        certificates = new TestCertificates(files);
    }

    private static AgentConfig.Connection connection(String hosts, @Nullable String user, @Nullable String ca, @Nullable String certificate,
            @Nullable String key, String mode)
    {
        return new AgentConfig.Connection(List.of(hosts.split(",")), user, user == null ? null : "secret", mode, ca, certificate, key,
                key == null ? null : TestCertificates.PASSPHRASE, Duration.ofSeconds(5));
    }

    @Test
    void sendsJsonAndReadsTheAnswer() throws Exception
    {
        try (FakeNode node = FakeNode.http().password("secret").answer("/_plugins/_security/api/roles", 200, "{\"status\":\"OK\"}")
                .answer("/empty", 200, "");
             RestOpenSearch search = new RestOpenSearch(connection("http://127.0.0.1:" + node.port(), "agent", null, null, null, "full"))) {
            assertThat(search.send("PATCH", "/_plugins/_security/api/roles", Map.of("pretty", "false"),
                    FakeOpenSearch.json("[{\"op\":\"remove\",\"path\":\"/x\"}]")).path("status").asText()).isEqualTo("OK");
            assertThat(search.get("/empty", Map.of()).isEmpty()).isTrue();
            assertThat(node.sent()).extracting(FakeNode.Sent::user).containsOnly("agent");
            assertThat(node.sent().get(0).query()).isEqualTo("pretty=false");
        }
    }

    @Test
    void namesWhyARequestFailed() throws Exception
    {
        try (FakeNode node = FakeNode.http().password("secret").answer("/denied", 403, "{\"error\":{\"reason\":\"no permissions\"}}")
                .answer("/status", 404, "{\"status\":\"NOT_FOUND\",\"message\":\"role 'x' not found.\"}").answer("/plain", 500, "broke\ndetails")
                .answer("/bare", 400, "");
             RestOpenSearch search = new RestOpenSearch(connection("http://127.0.0.1:" + node.port(), "agent", null, null, null, "full"))) {
            assertThatThrownBy(() -> search.get("/denied", Map.of())).isInstanceOfSatisfying(OpenSearchFailure.class,
                    failed -> assertThat(failed.status()).isEqualTo(403)).hasMessageEndingWith("403 no permissions").hasMessageStartingWith("GET /denied at");
            assertThatThrownBy(() -> search.get("/status", Map.of())).hasMessageEndingWith("404 role 'x' not found.");
            assertThatThrownBy(() -> search.get("/plain", Map.of())).hasMessageEndingWith("500 broke");
            assertThatThrownBy(() -> search.get("/bare", Map.of())).hasMessageEndingWith("400 Bad Request");
        }
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        try (RestOpenSearch search = new RestOpenSearch(connection("http://127.0.0.1:" + closed, "agent", null, null, null, "full"))) {
            assertThatThrownBy(() -> search.get("/", Map.of())).isInstanceOfSatisfying(OpenSearchFailure.class,
                    failed -> assertThat(failed.unreachable()).isTrue());
        }
        assertThat(RestOpenSearch.reason("{\"error\":\"plain\"}", "x")).isEqualTo("plain");
        assertThat(RestOpenSearch.reason("", "fallback")).isEqualTo("fallback");
        assertThat(RestOpenSearch.message(new IllegalStateException(null, new IllegalStateException("inner\nmore")))).isEqualTo("inner");
        assertThat(RestOpenSearch.message(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    void checksTheNodesAndSignsInWithAClientCertificate() throws Exception
    {
        try (FakeNode node = FakeNode.https(certificates.server(), true).answer("/", 200, "{\"ok\":true}")) {
            String url = "https://localhost:" + node.port();
            try (RestOpenSearch search = new RestOpenSearch(connection(url, null, certificates.file("ca.pem"), certificates.file("client.pem"),
                    certificates.file("client-encrypted.pem"), "full"))) {
                assertThat(search.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            }
            try (RestOpenSearch search = new RestOpenSearch(connection("https://127.0.0.1:" + node.port(), null, certificates.file("ca.pem"),
                    certificates.file("client.pem"), certificates.file("client-pkcs8.pem"), "full"))) {
                assertThatThrownBy(() -> search.get("/", Map.of())).as("the certificate is for localhost").isInstanceOf(OpenSearchFailure.class);
            }
            try (RestOpenSearch search = new RestOpenSearch(connection("https://127.0.0.1:" + node.port(), null, null, certificates.file("client.pem"),
                    certificates.file("client-pkcs8.pem"), "none"))) {
                assertThat(search.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            }
        }
        assertThatThrownBy(() -> new RestOpenSearch(connection("https://localhost:1", null, certificates.file("empty.pem"), null, null, "full")))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("the agent's TLS files cannot be used");
    }
}
