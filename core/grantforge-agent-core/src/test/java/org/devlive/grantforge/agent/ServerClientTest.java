// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerClientTest
{
    @TempDir
    Path cache;

    private FakeServer server;
    private ServerClient client;

    @BeforeEach
    void start() throws IOException
    {
        server = new FakeServer();
        client = new ServerClient(server.settings(cache).build());
    }

    @AfterEach
    void stop()
    {
        server.close();
    }

    @Test
    void sendsHeartbeatsWithTheToken() throws IOException
    {
        server.refreshSeconds(12);

        ServerClient.Heartbeat heartbeat = client.heartbeat(null);
        assertThat(heartbeat.policyVersion()).isEqualTo(1);
        assertThat(heartbeat.refreshSeconds()).isEqualTo(12);
        client.heartbeat(5L);
        JsonNode first = server.heartbeats.get(0);
        assertThat(first.path("instance").asText()).isEqualTo("test-agent");
        assertThat(first.path("host").asText()).isEqualTo("test-host");
        assertThat(first.path("agentVersion").asText()).isEqualTo("test");
        assertThat(first.path("appliedPolicyVersion").isNull()).isTrue();
        assertThat(server.heartbeats.get(1).path("appliedPolicyVersion").asLong()).isEqualTo(5);
    }

    @Test
    void downloadsSnapshotsUnlessTheAgentHasThem() throws IOException
    {
        ServerClient.Download download = client.policies(null);

        assertThat(download.changed()).isTrue();
        assertThat(new String(download.body(), StandardCharsets.UTF_8)).contains("\"warehouse\"");
        assertThat(download.etag()).isEqualTo(server.etag());
        assertThat(download.keyId()).isEqualTo(FakeServer.keyId(server.keys()));
        assertThat(SigningKey.of(FakeServer.publicKey(server.keys())).verifies(download.body(), download.signature())).isTrue();
        assertThat(client.policies(download.etag()).changed()).isFalse();
        assertThat(server.downloads.get()).isEqualTo(1);
    }

    @Test
    void fetchesTheSigningKey() throws IOException
    {
        assertThat(client.signingKey().keyId()).isEqualTo(FakeServer.keyId(server.keys()));
    }

    @Test
    void sendsEventsAndCountsWhatWasStored() throws IOException
    {
        Map<String, Object> event = AccessEvent.builder("alice", "r", "select", true).build().fields();

        assertThat(client.send(List.of(event, event))).isEqualTo(2);
        JsonNode batch = server.eventBatches.get(0);
        assertThat(batch.path("instance").asText()).isEqualTo("test-agent");
        assertThat(batch.path("events").get(0).path("user").asText()).isEqualTo("alice");
    }

    @Test
    void reportsRefusalsAndUnreachableServers() throws IOException
    {
        server.down(true);
        assertThatThrownBy(() -> client.heartbeat(null)).isInstanceOf(IOException.class).hasMessageContaining("503")
                .hasMessageContaining("Service Unavailable");
        ServerClient wrongToken = new ServerClient(server.settings(cache).token("gfa_wrong").build());
        server.down(false);
        assertThatThrownBy(() -> wrongToken.policies(null)).isInstanceOf(IOException.class).hasMessageContaining("401");
        server.close();
        assertThatThrownBy(() -> client.heartbeat(null)).isInstanceOf(IOException.class);
    }

    @Test
    void refusesAnswersItCannotUse() throws IOException
    {
        HttpServer odd = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        odd.createContext("/", exchange -> {
            byte[] body = "[1]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        odd.start();
        try {
            // The address may end in a slash or not.
            ServerClient oddClient = new ServerClient(server.settings(cache)
                    .server(URI.create("http://127.0.0.1:" + odd.getAddress().getPort() + "/")).build());
            assertThatThrownBy(() -> oddClient.heartbeat(null)).isInstanceOf(IOException.class).hasMessageContaining("JSON object");
            assertThatThrownBy(() -> oddClient.policies(null)).isInstanceOf(IOException.class).hasMessageContaining("signature");
        }
        finally {
            odd.stop(0);
        }
    }
}
