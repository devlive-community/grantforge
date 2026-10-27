// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

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

class RestElasticsearchTest
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
        return new AgentConfig.Connection(List.of(hosts.split(",")), user, user == null ? null : "secret", null, mode, ca, certificate, key,
                key == null ? null : TestCertificates.PASSPHRASE, Duration.ofSeconds(5));
    }

    @Test
    void sendsJsonAndReadsTheAnswer() throws Exception
    {
        try (FakeNode node = FakeNode.http().password("secret").answer("/_security/role", 200, "{\"created\":[\"x\"]}")
                .answer("/empty", 200, "");
             RestElasticsearch search = new RestElasticsearch(connection("http://127.0.0.1:" + node.port(), "agent", null, null, null, "full"))) {
            assertThat(search.send("POST", "/_security/role", Map.of("pretty", "false"), FakeElasticsearch.json("{\"roles\":{}}"))
                    .path("created").path(0).asText()).isEqualTo("x");
            assertThat(search.get("/empty", Map.of()).isEmpty()).isTrue();
            assertThat(node.sent()).as("every request carries the credentials at once").extracting(FakeNode.Sent::user).containsExactly("agent", "agent");
            assertThat(node.sent().get(0).query()).isEqualTo("pretty=false");
        }
        try (FakeNode node = FakeNode.http().answer("/", 200, "{\"ok\":true}");
             RestElasticsearch search = new RestElasticsearch(new AgentConfig.Connection(List.of("http://127.0.0.1:" + node.port()), null, null,
                     "VnVhQ2ZHY0I6dWkybHA=", "full", null, null, null, null, Duration.ofSeconds(5)))) {
            assertThat(search.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            assertThat(node.sent()).as("an API key is no Basic sign-in").extracting(FakeNode.Sent::user).containsExactly((String) null);
        }
    }

    @Test
    void namesWhyARequestFailed() throws Exception
    {
        try (FakeNode node = FakeNode.http().password("secret").answer("/denied", 403, "{\"error\":{\"reason\":\"no permissions\"}}")
                .answer("/status", 404, "{\"status\":\"NOT_FOUND\",\"message\":\"role 'x' not found.\"}").answer("/plain", 500, "broke\ndetails")
                .answer("/bare", 400, "");
             RestElasticsearch search = new RestElasticsearch(connection("http://127.0.0.1:" + node.port(), "agent", null, null, null, "full"))) {
            assertThatThrownBy(() -> search.get("/denied", Map.of())).isInstanceOfSatisfying(ElasticsearchFailure.class,
                    failed -> assertThat(failed.status()).isEqualTo(403)).hasMessageEndingWith("403 no permissions").hasMessageStartingWith("GET /denied at");
            assertThatThrownBy(() -> search.get("/status", Map.of())).hasMessageEndingWith("404 role 'x' not found.");
            assertThatThrownBy(() -> search.get("/plain", Map.of())).hasMessageEndingWith("500 broke");
            assertThatThrownBy(() -> search.get("/bare", Map.of())).hasMessageEndingWith("400 HTTP 400");
        }
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        try (RestElasticsearch search = new RestElasticsearch(connection("http://127.0.0.1:" + closed, "agent", null, null, null, "full"))) {
            assertThatThrownBy(() -> search.get("/", Map.of())).isInstanceOfSatisfying(ElasticsearchFailure.class,
                    failed -> assertThat(failed.unreachable()).isTrue());
        }
        assertThat(RestElasticsearch.reason("{\"error\":\"plain\"}", "x")).isEqualTo("plain");
        assertThat(RestElasticsearch.reason("", "fallback")).isEqualTo("fallback");
        assertThat(RestElasticsearch.message(new IllegalStateException(null, new IllegalStateException("inner\nmore")))).isEqualTo("inner");
        assertThat(RestElasticsearch.message(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    void checksTheNodesAndSignsInWithAClientCertificate() throws Exception
    {
        try (FakeNode node = FakeNode.https(certificates.server(), true).answer("/", 200, "{\"ok\":true}")) {
            String url = "https://localhost:" + node.port();
            try (RestElasticsearch search = new RestElasticsearch(connection(url, null, certificates.file("ca.pem"), certificates.file("client.pem"),
                    certificates.file("client-encrypted.pem"), "full"))) {
                assertThat(search.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            }
            try (RestElasticsearch search = new RestElasticsearch(connection("https://127.0.0.1:" + node.port(), null, certificates.file("ca.pem"),
                    certificates.file("client.pem"), certificates.file("client-pkcs8.pem"), "full"))) {
                assertThatThrownBy(() -> search.get("/", Map.of())).as("the certificate is for localhost").isInstanceOf(ElasticsearchFailure.class);
            }
            try (RestElasticsearch search = new RestElasticsearch(connection("https://127.0.0.1:" + node.port(), null, null, certificates.file("client.pem"),
                    certificates.file("client-pkcs8.pem"), "none"))) {
                assertThat(search.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            }
        }
        assertThatThrownBy(() -> new RestElasticsearch(connection("https://localhost:1", null, certificates.file("empty.pem"), null, null, "full")))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("the agent's TLS files cannot be used");
    }
}
