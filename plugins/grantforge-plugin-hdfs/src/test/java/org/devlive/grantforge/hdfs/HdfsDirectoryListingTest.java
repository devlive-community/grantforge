// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsDirectoryListingTest
{
    @TempDir
    Path temporary;

    private static String entry(String name, String type)
    {
        return "{\"pathSuffix\":\"" + name + "\",\"type\":\"" + type + "\",\"length\":0,\"owner\":\"hdfs\",\"group\":\"supergroup\","
                + "\"permission\":\"755\",\"accessTime\":0,\"modificationTime\":0,\"blockSize\":134217728,\"replication\":1,"
                + "\"fileId\":1,\"childrenNum\":0,\"storagePolicy\":0}";
    }

    private static String listing(String... entries)
    {
        return "{\"FileStatuses\":{\"FileStatus\":[" + String.join(",", entries) + "]}}";
    }

    private static List<String> lookup(ServiceConfig config)
    {
        return new HdfsProvider().lookup(new LookupRequest(config, "path", "/data/", Map.of(), 1));
    }

    @Test
    void browsesAndTestsServersThatRejectTheNewBatchOperation() throws IOException
    {
        try (OldWebHdfs server = new OldWebHdfs(200, listing(entry("notes", "FILE"), entry("alice", "DIRECTORY")), false)) {
            ServiceConfig config = server.config("100");
            assertThat(new HdfsProvider().testConnection(config).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(lookup(config)).containsExactly("/data/alice");
            assertThat(server.requests).anyMatch(query -> query.contains("op=LISTSTATUS"))
                    .noneMatch(query -> query.contains("LISTSTATUS_BATCH"));
        }
    }

    @Test
    void enforcesTheScanLimitRatherThanOnlyTheSuggestionCount() throws IOException
    {
        try (OldWebHdfs server = new OldWebHdfs(200, listing(entry("a", "FILE"), entry("b", "FILE")), false)) {
            ServiceConfig config = server.config("1");
            assertThatThrownBy(() -> lookup(config)).isInstanceOf(UncheckedIOException.class).hasMessageContaining("exceeds 1 entries");
            assertThat(new HdfsProvider().testConnection(config).status()).isEqualTo(ConnectionResult.Status.FAILED);
        }
        Files.writeString(temporary.resolve("a"), "a");
        Files.writeString(temporary.resolve("b"), "b");
        ServiceConfig local = HdfsProviderTest.config("file:///", "lookup.path", temporary.toString(), "lookup.max.entries", "1");
        assertThat(new HdfsProvider().testConnection(local).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void boundsKnownLengthAndChunkedSuccessfulBodies() throws IOException
    {
        String tooLarge = "{\"ignored\":\"" + "x".repeat(100000) + "\",\"FileStatuses\":{\"FileStatus\":[]}}";
        for (boolean chunked : new boolean[] {false, true}) {
            try (OldWebHdfs server = new OldWebHdfs(200, tooLarge, chunked)) {
                assertThatThrownBy(() -> lookup(server.config("1"))).isInstanceOf(UncheckedIOException.class)
                        .hasMessageContaining("bytes");
            }
        }
    }

    @Test
    void boundsTheMetadataResponseBeforeAttemptingAnyDirectoryListing() throws IOException
    {
        for (boolean chunked : new boolean[] {false, true}) {
            try (OldWebHdfs server = new OldWebHdfs(200, listing(), chunked)) {
                server.statusBody = "{\"ignored\":\"" + "x".repeat(100000) + "\",\"FileStatus\":" + entry("", "DIRECTORY") + "}";
                assertThatThrownBy(() -> lookup(server.config("100"))).isInstanceOf(UncheckedIOException.class).hasMessageContaining("bytes");
                assertThat(server.requests).noneMatch(query -> query.contains("op=LISTSTATUS"));
                assertThat(new HdfsProvider().testConnection(server.config("100")).status()).isEqualTo(ConnectionResult.Status.FAILED);
            }
        }
    }

    @Test
    void validatesJsonMediaTypesWithoutDependingOnHadoopsMissingShadedJerseyProvider() throws IOException
    {
        try (OldWebHdfs server = new OldWebHdfs(200, listing(entry("a", "FILE")), false)) {
            server.contentType = "application/json; charset=UTF-8";
            assertThat(lookup(server.config("100"))).containsExactly("/data/a");
            server.contentType = "text/html";
            assertThatThrownBy(() -> lookup(server.config("100"))).isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("Content-Type");
            assertThat(new HdfsProvider().testConnection(server.config("100")).status()).isEqualTo(ConnectionResult.Status.FAILED);
        }
    }

    @Test
    void boundsErrorBodiesAndPreservesPermissionNetworkAndMalformedResponseFailures() throws IOException
    {
        String denied = "{\"RemoteException\":{\"exception\":\"AccessControlException\",\"javaClassName\":"
                + "\"org.apache.hadoop.security.AccessControlException\",\"message\":\"Permission denied\"}}";
        try (OldWebHdfs server = new OldWebHdfs(403, denied, false)) {
            assertThatThrownBy(() -> lookup(server.config("100"))).isInstanceOf(UncheckedIOException.class).hasMessageContaining("Permission denied");
            assertThat(new HdfsProvider().testConnection(server.config("100")).status()).isEqualTo(ConnectionResult.Status.FAILED);
        }
        String oversizedError = "{\"RemoteException\":{\"exception\":\"IOException\",\"javaClassName\":\"java.io.IOException\","
                + "\"message\":\"" + "x".repeat(100000) + "\"}}";
        try (OldWebHdfs server = new OldWebHdfs(500, oversizedError, true)) {
            assertThatThrownBy(() -> lookup(server.config("100"))).isInstanceOf(UncheckedIOException.class)
                    .hasRootCauseMessage("WebHDFS response exceeds 65536 bytes");
        }
        try (OldWebHdfs server = new OldWebHdfs(200, "{\"wrong\":[]}", false)) {
            assertThatThrownBy(() -> lookup(server.config("100"))).isInstanceOf(UncheckedIOException.class).hasMessageContaining("LISTSTATUS");
        }
        OldWebHdfs stopped = new OldWebHdfs(200, listing(), false);
        ServiceConfig config = stopped.config("100");
        stopped.close();
        assertThatThrownBy(() -> lookup(config)).isInstanceOf(UncheckedIOException.class);
    }

    private static final class OldWebHdfs
            implements AutoCloseable
    {
        final List<String> requests = new CopyOnWriteArrayList<>();
        private final HttpServer server;
        private final int status;
        private final String body;
        private final boolean chunked;
        private volatile String statusBody = "{\"FileStatus\":" + entry("", "DIRECTORY") + "}";
        private volatile String contentType = "application/json";

        OldWebHdfs(int status, String body, boolean chunked) throws IOException
        {
            this.status = status;
            this.body = body;
            this.chunked = chunked;
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/webhdfs/v1", this::respond);
            server.start();
        }

        ServiceConfig config(String maximum)
        {
            return HdfsProviderTest.config("webhdfs://127.0.0.1:" + server.getAddress().getPort(), "lookup.path", "/data",
                    "lookup.max.entries", maximum, "hadoop.config", "dfs.http.client.retry.policy.enabled=false");
        }

        private void respond(HttpExchange exchange) throws IOException
        {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(query);
            int code = status;
            String response = body;
            if (query.contains("op=GETFILESTATUS")) {
                code = 200;
                response = statusBody;
            }
            else if (query.contains("LISTSTATUS_BATCH")) {
                code = 400;
                response = "{\"RemoteException\":{\"exception\":\"IllegalArgumentException\",\"javaClassName\":\"java.lang.IllegalArgumentException\","
                        + "\"message\":\"Invalid value for webhdfs parameter op: LISTSTATUS_BATCH\"}}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(code, chunked ? 0 : bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }

        @Override
        public void close()
        {
            server.stop(0);
        }
    }
}
