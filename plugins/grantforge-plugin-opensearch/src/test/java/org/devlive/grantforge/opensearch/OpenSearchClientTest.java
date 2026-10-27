// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchClientTest
{
    @TempDir
    static Path files;

    private static TestCertificates certificates;

    @BeforeAll
    static void certificates() throws Exception
    {
        certificates = new TestCertificates(files);
    }

    private static OpenSearchSettings settings(String... values)
    {
        return OpenSearchSettings.of(OpenSearchSettingsTest.config(values));
    }

    private static OpenSearchClient client(FakeOpenSearch node, String... values)
    {
        String[] all = new String[values.length + 2];
        all[0] = OpenSearchSettings.HOSTS;
        all[1] = "http://127.0.0.1:" + node.port();
        System.arraycopy(values, 0, all, 2, values.length);
        return OpenSearchClient.open(settings(all));
    }

    private static int closedPort() throws Exception
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void readsJsonWithItsParametersAndSignsInWhenAsked() throws Exception
    {
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret").answer("/logs-*/_field_caps", 200, "{\"fields\":{\"a\":{}}}");
             OpenSearchClient client = client(node)) {
            JsonNode read = client.get(OpenSearchLookup.path("logs-*", "_field_caps"), Map.of("fields", "*"));
            assertThat(read.path("fields").has("a")).isTrue();
            assertThat(node.sent()).extracting(FakeOpenSearch.Sent::user).containsExactly("lookup");
            assertThat(node.sent().get(0).query()).isEqualTo("fields=*");
        }
    }

    @Test
    void namesWhyARequestWasRefused() throws Exception
    {
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret")
                .answer("/denied", 403, "{\"error\":{\"type\":\"security_exception\",\"reason\":\"no permissions for [indices:data/read/search]\"}}")
                .answer("/broken", 500, "{\"error\":\"something broke\\nwith details\"}").answer("/busy", 503, "")
                .answer("/plain", 400, "bad things\nhappened");
             OpenSearchClient client = client(node)) {
            assertThatThrownBy(() -> client.get("/denied", Map.of())).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED))
                    .hasMessage("http://127.0.0.1:" + node.port() + ": 403 no permissions for [indices:data/read/search]");
            assertThatThrownBy(() -> client.get("/missing", Map.of())).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.NOT_FOUND)).hasMessageEndingWith("404 no such index");
            assertThatThrownBy(() -> client.get("/broken", Map.of())).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED)).hasMessageEndingWith("500 something broke");
            assertThatThrownBy(() -> client.get("/busy", Map.of())).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
            assertThatThrownBy(() -> client.get("/plain", Map.of())).hasMessageEndingWith("400 bad things");
        }
        try (FakeOpenSearch node = FakeOpenSearch.http().password("other"); OpenSearchClient client = client(node)) {
            assertThatThrownBy(() -> client.get("/", Map.of())).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED)).hasMessageEndingWith("401 Unauthorized");
        }
    }

    @Test
    void findsNothingWhereTheClusterHasNoSuchResourceOrHandler() throws Exception
    {
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret")
                .answer("/_plugins/_security/authinfo", 400, "{\"error\":\"no handler found for uri [/_plugins/_security/authinfo] and method [GET]\"}")
                .answer("/bad", 400, "{\"error\":{\"reason\":\"invalid index name\"}}").answer("/there", 200, "{}");
             OpenSearchClient client = client(node)) {
            assertThat(client.find("/_plugins/_security/authinfo", Map.of())).isNull();
            assertThat(client.find("/missing", Map.of())).isNull();
            assertThat(client.find("/there", Map.of())).isNotNull();
            assertThatThrownBy(() -> client.find("/bad", Map.of())).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
        }
    }

    @Test
    void goesToTheNextNodeAndNamesTheNodesWhenNoneCanBeReached() throws Exception
    {
        int closed = closedPort();
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret").answer("/", 200, "{\"cluster_name\":\"c\"}");
             OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "http://127.0.0.1:" + closed + ",http://127.0.0.1:"
                     + node.port()))) {
            assertThat(client.get("/", Map.of()).path("cluster_name").asText()).isEqualTo("c");
        }
        try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "http://127.0.0.1:" + closed))) {
            assertThatThrownBy(() -> client.get("/", Map.of())).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE)).hasMessageStartingWith("http://127.0.0.1:" + closed);
        }
    }

    @Test
    void checksTheNodesCertificateAsTheModeSays() throws Exception
    {
        try (FakeOpenSearch node = FakeOpenSearch.https(certificates.server(), false).password("secret").answer("/", 200, "{\"ok\":true}")) {
            String ca = certificates.file("ca.pem");
            try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "https://localhost:" + node.port(),
                    OpenSearchSettings.CERTIFICATE_AUTHORITIES, ca))) {
                assertThat(client.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            }
            try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "https://127.0.0.1:" + node.port(),
                    OpenSearchSettings.CERTIFICATE_AUTHORITIES, ca))) {
                assertThatThrownBy(() -> client.get("/", Map.of())).as("the certificate is for localhost only")
                        .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED))
                        .hasMessageContaining("TLS failed");
            }
            try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "https://127.0.0.1:" + node.port(),
                    OpenSearchSettings.VERIFICATION_MODE, "certificate", OpenSearchSettings.CERTIFICATE_AUTHORITIES, ca))) {
                assertThat(client.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            }
            try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "https://localhost:" + node.port()))) {
                assertThatThrownBy(() -> client.get("/", Map.of())).as("the JVM does not trust the test CA").hasMessageContaining("TLS failed");
            }
            try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "https://127.0.0.1:" + node.port(),
                    OpenSearchSettings.VERIFICATION_MODE, "none"))) {
                assertThat(client.get("/", Map.of()).path("ok").asBoolean()).isTrue();
            }
        }
    }

    @Test
    void signsInWithAClientCertificate() throws Exception
    {
        try (FakeOpenSearch node = FakeOpenSearch.https(certificates.server(), true).answer("/", 200, "{\"ok\":true}")) {
            try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "https://localhost:" + node.port(),
                    OpenSearchSettings.CERTIFICATE_AUTHORITIES, certificates.file("ca.pem"), OpenSearchSettings.AUTHENTICATION, "certificate",
                    OpenSearchSettings.USER, OpenSearchSettingsTest.UNSET, OpenSearchSettings.PASSWORD, OpenSearchSettingsTest.UNSET,
                    OpenSearchSettings.CERTIFICATE, certificates.file("client.pem"), OpenSearchSettings.KEY, certificates.file("client-encrypted.pem"),
                    OpenSearchSettings.KEY_PASSPHRASE, TestCertificates.PASSPHRASE))) {
                assertThat(client.get("/", Map.of()).path("ok").asBoolean()).isTrue();
                assertThat(node.sent()).extracting(FakeOpenSearch.Sent::user).containsExactly((String) null);
            }
            try (OpenSearchClient client = OpenSearchClient.open(settings(OpenSearchSettings.HOSTS, "https://localhost:" + node.port(),
                    OpenSearchSettings.CERTIFICATE_AUTHORITIES, certificates.file("ca.pem")))) {
                assertThatThrownBy(() -> client.get("/", Map.of())).as("the node wants a certificate").isInstanceOf(LookupException.class);
            }
        }
    }

    @Test
    void tlsFilesThatCannotBeReadOrUsedAreNamed()
    {
        assertThatThrownBy(() -> OpenSearchClient.open(settings(OpenSearchSettings.CERTIFICATE_AUTHORITIES, files.resolve("nowhere.pem").toString())))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED))
                .hasMessageStartingWith("a TLS file of the service cannot be read");
        assertThatThrownBy(() -> OpenSearchClient.open(settings(OpenSearchSettings.CERTIFICATE_AUTHORITIES, certificates.file("empty.pem"))))
                .hasMessageStartingWith("a TLS file of the service cannot be used");
    }

    @Test
    void readsWhatAnErrorSays()
    {
        assertThat(OpenSearchClient.reason("{\"error\":{\"reason\":\"why\"}}", "fallback")).isEqualTo("why");
        assertThat(OpenSearchClient.reason("{\"error\":\"plain why\"}", "fallback")).isEqualTo("plain why");
        assertThat(OpenSearchClient.reason("{\"status\":\"error\"}", "fallback")).isEqualTo("{\"status\":\"error\"}");
        assertThat(OpenSearchClient.reason("", "fallback")).isEqualTo("fallback");
        assertThat(OpenSearchClient.reason(401)).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(OpenSearchClient.reason(429)).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(OpenSearchClient.reason(409)).isEqualTo(LookupException.Reason.FAILED);
        assertThat(OpenSearchClient.message(new IllegalStateException())).isEqualTo("IllegalStateException");
        assertThat(OpenSearchClient.message(new IllegalStateException("first\nsecond"))).isEqualTo("first");
    }
}
