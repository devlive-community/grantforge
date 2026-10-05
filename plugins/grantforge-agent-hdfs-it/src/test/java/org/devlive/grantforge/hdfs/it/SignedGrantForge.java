// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/** Serves the real agent HTTP contract and signs every snapshot with an Ed25519 key; no agent objects are mocked. */
final class SignedGrantForge
        implements AutoCloseable
{
    static final String TOKEN = "gfa_hdfs-integration-token";

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpServer server;
    private final KeyPair keys;
    private final String keyId;
    private final List<JsonNode> events = new CopyOnWriteArrayList<>();
    private final List<JsonNode> heartbeats = new CopyOnWriteArrayList<>();
    private final AtomicInteger downloads = new AtomicInteger();
    private volatile byte[] snapshot;
    private volatile boolean available = true;

    SignedGrantForge(String owner) throws IOException, GeneralSecurityException
    {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keys.getPublic().getEncoded())).substring(0, 16);
        snapshot = policies(owner);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/agent/heartbeat", this::heartbeat);
        server.createContext("/api/v1/agent/policies", this::policies);
        server.createContext("/api/v1/agent/signing-key", this::signingKey);
        server.createContext("/api/v1/agent/access-events", this::audit);
        server.start();
    }

    URI uri()
    {
        String host = server.getAddress().getAddress().getHostAddress();
        String address = host.indexOf(':') < 0 ? host : "[" + host + "]";
        return URI.create("http://" + address + ":" + server.getAddress().getPort() + "/");
    }

    String publicKey()
    {
        return Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    int downloads()
    {
        return downloads.get();
    }

    void available(boolean value)
    {
        available = value;
    }

    boolean hasInstance(String instance)
    {
        return heartbeats.stream().anyMatch(heartbeat -> instance.equals(heartbeat.path("instance").asText()));
    }

    boolean denied(String resource, String enforcer)
    {
        return events.stream().anyMatch(event -> "alice".equals(event.path("user").asText())
                && resource.equals(event.path("resource").asText()) && "DENIED".equals(event.path("outcome").asText())
                && enforcer.equals(event.path("enforcer").asText()));
    }

    private static byte[] policies(String owner) throws IOException
    {
        List<Map<String, Object>> policies = new ArrayList<>();
        policies.add(policy(1, "administrator", "/", true, "allow", owner, List.of("read", "write", "execute")));
        policies.add(policy(2, "traversal", "/", true, "allow", "alice", List.of("execute")));
        policies.add(policy(3, "data access", "/data", true, "allow", "alice", List.of("read", "write", "execute")));
        policies.add(policy(4, "secret read", "/data/secret", false, "deny", "alice", List.of("read")));
        policies.add(policy(5, "protected mutation", "/data/protected", false, "deny", "alice", List.of("write")));
        policies.add(policy(6, "blocked destination", "/data/blocked", false, "deny", "alice", List.of("write")));
        policies.add(policy(7, "protected descendant", "/data/private/child", false, "deny", "alice", List.of("write")));
        Map<String, Object> definition = Map.of("resources", List.of(Map.of("name", "path", "matcher", "PATH", "caseSensitive", true)),
                "accessTypes", List.of(access("read"), access("write"), access("execute")), "conditions", List.of());
        return JSON.writeValueAsBytes(Map.of("format", 1, "service", "integration-cluster", "serviceType", "hdfs", "serviceEnabled", true,
                "policyVersion", 1, "definition", definition, "policies", policies, "roles", Map.of(), "groups", Map.of()));
    }

    private static Map<String, Object> access(String name)
    {
        return Map.of("name", name, "impliedGrants", List.of());
    }

    private static Map<String, Object> policy(long id, String name, String path, boolean recursive, String list, String user,
            List<String> accessTypes)
    {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("resources", Map.of("path", Map.of("values", List.of(path), "excludes", false, "recursive", recursive)));
        document.put(list, List.of(Map.of("users", List.of(user), "groups", List.of(), "roles", List.of(), "accessTypes", accessTypes)));
        return Map.of("id", Long.toString(id), "type", "ACCESS", "name", name, "priority", "NORMAL", "document", document);
    }

    private boolean refused(HttpExchange exchange) throws IOException
    {
        if (!available) {
            respond(exchange, 503, "{\"title\":\"unavailable\"}".getBytes(StandardCharsets.UTF_8));
            return true;
        }
        if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, "{\"title\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8));
            return true;
        }
        return false;
    }

    private void heartbeat(HttpExchange exchange) throws IOException
    {
        if (!refused(exchange)) {
            heartbeats.add(requireNonNull(JSON.readTree(exchange.getRequestBody())));
            respond(exchange, 200, "{\"policyVersion\":1,\"refreshSeconds\":1}".getBytes(StandardCharsets.UTF_8));
        }
    }

    private void policies(HttpExchange exchange) throws IOException
    {
        if (refused(exchange)) {
            return;
        }
        String etag = "\"snapshot-1\"";
        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.getResponseHeaders().set("ETag", etag);
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        byte[] body = snapshot;
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(keys.getPrivate());
            signature.update(body);
            exchange.getResponseHeaders().set("X-GrantForge-Signature", Base64.getEncoder().encodeToString(signature.sign()));
        }
        catch (GeneralSecurityException invalid) {
            throw new IOException("cannot sign the integration snapshot", invalid);
        }
        exchange.getResponseHeaders().set("ETag", etag);
        exchange.getResponseHeaders().set("X-GrantForge-Signing-Key", keyId);
        exchange.getResponseHeaders().set("X-GrantForge-Policy-Version", "1");
        downloads.incrementAndGet();
        respond(exchange, 200, body);
    }

    private void signingKey(HttpExchange exchange) throws IOException
    {
        if (!refused(exchange)) {
            respond(exchange, 200, JSON.writeValueAsBytes(Map.of("keyId", keyId, "algorithm", "Ed25519", "publicKey", publicKey())));
        }
    }

    private void audit(HttpExchange exchange) throws IOException
    {
        if (!refused(exchange)) {
            JsonNode batch = requireNonNull(JSON.readTree(exchange.getRequestBody()));
            batch.path("events").forEach(events::add);
            respond(exchange, 200, JSON.writeValueAsBytes(Map.of("accepted", batch.path("events").size(), "duplicates", 0, "expired", 0)));
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close()
    {
        server.stop(0);
    }
}
