// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The agent API of a GrantForge server, for agents in containers: heartbeats, policy snapshots signed with an Ed25519
 * key, the signing key and access events. Policies change with {@link #publish}; agents see them at their next
 * heartbeat.
 */
final class AgentServer
        implements AutoCloseable
{
    static final String TOKEN = "gfa_solr-agent-test";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final KeyPair keys;
    private final List<JsonNode> events = new CopyOnWriteArrayList<>();
    private volatile byte[] snapshot;
    private volatile long version;

    AgentServer(SolrSnapshots initial) throws IOException, GeneralSecurityException
    {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        snapshot = initial.version(1).json();
        version = 1;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/agent/heartbeat", this::heartbeat);
        server.createContext("/api/v1/agent/policies", this::policies);
        server.createContext("/api/v1/agent/signing-key", this::signingKey);
        server.createContext("/api/v1/agent/access-events", this::accessEvents);
        server.start();
    }

    int port()
    {
        return server.getAddress().getPort();
    }

    /** The public key agents pin, as the server shows it. */
    String publicKey()
    {
        return Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    /** Replaces the policies with those of a snapshot whose version is the next. */
    void publish(SolrSnapshots policies)
    {
        long next = version + 1;
        snapshot = policies.version(next).json();
        version = next;
    }

    /** The access events agents sent so far. */
    List<JsonNode> events()
    {
        return new ArrayList<>(events);
    }

    private boolean refused(HttpExchange exchange) throws IOException
    {
        if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "{\"title\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8));
            return true;
        }
        return false;
    }

    private void heartbeat(HttpExchange exchange) throws IOException
    {
        if (!refused(exchange)) {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, JSON.writeValueAsBytes(JSON.createObjectNode().put("policyVersion", version).put("refreshSeconds", 1)));
        }
    }

    private void policies(HttpExchange exchange) throws IOException
    {
        if (refused(exchange)) {
            return;
        }
        byte[] body = snapshot;
        String etag = "\"" + version + "\"";
        exchange.getResponseHeaders().set("ETag", etag);
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("X-GrantForge-Policy-Version", Long.toString(version));
        exchange.getResponseHeaders().set("X-GrantForge-Signing-Key", keyId());
        exchange.getResponseHeaders().set("X-GrantForge-Signature", sign(body));
        respond(exchange, 200, body);
    }

    private void signingKey(HttpExchange exchange) throws IOException
    {
        if (!refused(exchange)) {
            respond(exchange, 200, JSON.writeValueAsBytes(JSON.createObjectNode().put("keyId", keyId()).put("algorithm", "Ed25519")
                    .put("publicKey", publicKey())));
        }
    }

    private void accessEvents(HttpExchange exchange) throws IOException
    {
        if (refused(exchange)) {
            return;
        }
        JsonNode batch = JSON.readTree(exchange.getRequestBody());
        batch.path("events").forEach(events::add);
        respond(exchange, 200, JSON.writeValueAsBytes(JSON.createObjectNode().put("accepted", batch.path("events").size()).put("duplicates", 0)
                .put("expired", 0)));
    }

    private String sign(byte[] body)
    {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keys.getPrivate());
            signature.update(body);
            return Base64.getEncoder().encodeToString(signature.sign());
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** The server's key id: the first 16 hex digits of the SHA-256 of the X.509 key. */
    private String keyId()
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keys.getPublic().getEncoded())).substring(0, 16);
        }
        catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
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
