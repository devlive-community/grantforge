// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The agent API of a GrantForge server, as far as agents see it: heartbeats, signed snapshots with ETags, the signing key
 * and access events. Signs with a real Ed25519 key from the JDK, like the server does.
 */
final class FakeServer
        implements AutoCloseable
{
    static final String TOKEN = "gfa_test-token";
    private static final ObjectMapper JSON = new ObjectMapper();

    final List<JsonNode> heartbeats = new CopyOnWriteArrayList<>();
    final List<JsonNode> eventBatches = new CopyOnWriteArrayList<>();
    final AtomicInteger downloads = new AtomicInteger();
    final AtomicInteger keyRequests = new AtomicInteger();

    private final HttpServer server;
    private volatile KeyPair keys = keyPair();
    private volatile byte[] snapshot = Snapshots.json(1, true).getBytes(StandardCharsets.UTF_8);
    private volatile long policyVersion = 1;
    private volatile boolean down;
    private volatile boolean tamper;
    private volatile long refreshSeconds = 30;

    FakeServer() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/agent/heartbeat", this::heartbeat);
        server.createContext("/api/v1/agent/policies", this::policies);
        server.createContext("/api/v1/agent/signing-key", this::signingKey);
        server.createContext("/api/v1/agent/access-events", this::accessEvents);
        server.start();
    }

    static KeyPair keyPair()
    {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String sign(KeyPair keys, byte[] data)
    {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keys.getPrivate());
            signature.update(data);
            return Base64.getEncoder().encodeToString(signature.sign());
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String publicKey(KeyPair keys)
    {
        return Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    /** The server's key id: the first 16 hex digits of the SHA-256 of the X.509 key. */
    static String keyId(KeyPair keys)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keys.getPublic().getEncoded())).substring(0, 16);
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Settings that reach this server, with short timeouts and batches of two events. */
    AgentSettings.Builder settings(java.nio.file.Path cache)
    {
        return AgentSettings.builder().server(uri()).token(TOKEN).instance("test-agent").host("test-host").agentVersion("test")
                .cacheDirectory(cache).timeouts(java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(5))
                .refreshInterval(java.time.Duration.ofSeconds(7)).audit(2, java.time.Duration.ofMillis(50), 4);
    }

    URI uri()
    {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    KeyPair keys()
    {
        return keys;
    }

    void publish(String json, long version)
    {
        snapshot = json.getBytes(StandardCharsets.UTF_8);
        policyVersion = version;
    }

    void rotateKey()
    {
        keys = keyPair();
    }

    void down(boolean value)
    {
        down = value;
    }

    /** Sends snapshots whose body no longer matches the signature. */
    void tamper(boolean value)
    {
        tamper = value;
    }

    void refreshSeconds(long value)
    {
        refreshSeconds = value;
    }

    String etag()
    {
        return "\"" + Integer.toHexString(java.util.Arrays.hashCode(snapshot)) + "\"";
    }

    private boolean refused(HttpExchange exchange) throws IOException
    {
        if (down) {
            respond(exchange, 503, "{\"title\":\"Service Unavailable\"}");
            return true;
        }
        if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "{\"title\":\"Unauthorized\"}");
            return true;
        }
        return false;
    }

    private void heartbeat(HttpExchange exchange) throws IOException
    {
        if (refused(exchange)) {
            return;
        }
        heartbeats.add(JSON.readTree(exchange.getRequestBody()));
        respond(exchange, 200, "{\"policyVersion\":" + policyVersion + ",\"refreshSeconds\":" + refreshSeconds + "}");
    }

    private void policies(HttpExchange exchange) throws IOException
    {
        if (refused(exchange)) {
            return;
        }
        String etag = etag();
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.getResponseHeaders().set("ETag", etag);
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        downloads.incrementAndGet();
        byte[] body = snapshot;
        String signature = sign(keys, body);
        if (tamper) {
            body = new String(body, StandardCharsets.UTF_8).replace("\"allow\"", "\"deny\"").getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("ETag", etag);
        exchange.getResponseHeaders().set(ServerClient.VERSION_HEADER, Long.toString(policyVersion));
        exchange.getResponseHeaders().set(ServerClient.KEY_HEADER, keyId(keys));
        exchange.getResponseHeaders().set(ServerClient.SIGNATURE_HEADER, signature);
        respond(exchange, 200, body);
    }

    private void signingKey(HttpExchange exchange) throws IOException
    {
        if (refused(exchange)) {
            return;
        }
        keyRequests.incrementAndGet();
        respond(exchange, 200, "{\"keyId\":\"" + keyId(keys) + "\",\"algorithm\":\"Ed25519\",\"publicKey\":\"" + publicKey(keys) + "\"}");
    }

    private void accessEvents(HttpExchange exchange) throws IOException
    {
        if (refused(exchange)) {
            return;
        }
        JsonNode batch = JSON.readTree(exchange.getRequestBody());
        eventBatches.add(batch);
        respond(exchange, 200, "{\"accepted\":" + batch.path("events").size() + ",\"duplicates\":0,\"expired\":0}");
    }

    int eventsReceived()
    {
        int count = 0;
        for (JsonNode batch : eventBatches) {
            count += batch.path("events").size();
        }
        return count;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException
    {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close()
    {
        server.stop(0);
    }
}
