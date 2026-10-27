// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * An Elasticsearch cluster in memory: its native users, roles and role mappings, which the security API reads and
 * changes as Elasticsearch does, roles in bulk with an error per role refused, and answers set for other requests, such
 * as the license, the resolve API, mappings and the validate API. Requests it has no answer for fail with 404; failures
 * may be set per request.
 */
final class FakeElasticsearch
        implements Elasticsearch
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USER = "/_security/user/";
    private static final String MAPPING = "/_security/role_mapping/";

    /**
     * A request the cluster was sent.
     *
     * @param method the method
     * @param path the path
     * @param parameters the query parameters
     * @param body the body, or {@code null}
     */
    record Sent(String method, String path, Map<String, String> parameters, @Nullable JsonNode body)
    {
    }

    final ObjectNode users = JSON.createObjectNode();
    final ObjectNode roles = JSON.createObjectNode();
    final ObjectNode mappings = JSON.createObjectNode();
    private final Map<String, Function<@Nullable JsonNode, JsonNode>> answers = new HashMap<>();
    private final Map<String, ElasticsearchFailure> failures = new HashMap<>();
    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> refused = new HashSet<>();
    private boolean closed;

    FakeElasticsearch()
    {
        answer("GET /", json("{\"cluster_name\": \"search\", \"version\": {\"number\": \"9.5.5\", \"build_flavor\": \"default\"}}"));
        answer("GET /_xpack", json("{\"features\": {\"security\": {\"available\": true, \"enabled\": true}}}"));
        answer("GET /_security/_authenticate", json("{\"username\": \"agent\"}"));
        license("platinum", "active");
        answer("GET /_resolve/index/*", json("{\"indices\": [], \"data_streams\": []}"));
        answers.put("POST /*,-.*/_validate/query", body -> json(String.valueOf(body).contains("bogus") ? "{\"valid\": false, \"error\": \"unknown query [bogus]\"}"
                : "{\"valid\": true}"));
    }

    static JsonNode json(String text)
    {
        try {
            return JSON.readTree(text);
        }
        catch (IOException broken) {
            throw new UncheckedIOException(broken);
        }
    }

    /** Sets the answer of a request, named as {@code METHOD path}. */
    FakeElasticsearch answer(String request, JsonNode answer)
    {
        answers.put(request, body -> answer);
        return this;
    }

    /** Sets the cluster's license. */
    FakeElasticsearch license(String type, String status)
    {
        return answer("GET /_license", json("{\"license\": {\"type\": \"" + type + "\", \"status\": \"" + status + "\"}}"));
    }

    /** Makes a request, named as {@code METHOD path}, fail. */
    FakeElasticsearch fail(String request, int status)
    {
        failures.put(request, new ElasticsearchFailure(request + ": " + status + " refused", status, null));
        return this;
    }

    /** Lets a request that was set to fail succeed again. */
    FakeElasticsearch heal(String request)
    {
        failures.remove(request);
        return this;
    }

    /** Makes Elasticsearch refuse every write of a role, role mapping or user of a name. */
    FakeElasticsearch refuse(String name)
    {
        refused.add(name);
        return this;
    }

    /** Adds a native user with roles. */
    FakeElasticsearch user(String name, String... roles)
    {
        ObjectNode user = users.putObject(name);
        user.put("username", name).put("full_name", name.toUpperCase(Locale.ROOT)).putNull("email").put("enabled", true);
        user.putObject("metadata");
        for (String role : roles) {
            user.withArray("/roles").add(role);
        }
        user.withArray("/roles");
        return this;
    }

    /** Adds a user Elasticsearch reserves, such as kibana_system. */
    FakeElasticsearch reserved(String name)
    {
        user(name);
        ((ObjectNode) users.path(name).path("metadata")).put("_reserved", true);
        return this;
    }

    /** The requests the cluster was sent, in order. */
    List<Sent> sent()
    {
        return new ArrayList<>(sent);
    }

    /** The requests of one method and path. */
    List<Sent> sent(String method, String path)
    {
        return sent.stream().filter(each -> each.method().equals(method) && each.path().equals(path)).toList();
    }

    boolean closed()
    {
        return closed;
    }

    @Override
    public JsonNode send(String method, String path, Map<String, String> parameters, @Nullable JsonNode body)
    {
        sent.add(new Sent(method, path, Map.copyOf(parameters), body == null ? null : body.deepCopy()));
        String request = method + " " + path;
        ElasticsearchFailure failure = failures.get(request);
        if (failure != null) {
            throw failure;
        }
        JsonNode security = security(method, path, body);
        if (security != null) {
            return security;
        }
        Function<@Nullable JsonNode, JsonNode> answer = answers.get(request);
        if (answer == null) {
            throw new ElasticsearchFailure(request + ": 404 no such resource", 404, null);
        }
        return answer.apply(body);
    }

    /** Answers the security API, or {@code null} for another request. */
    private @Nullable JsonNode security(String method, String path, @Nullable JsonNode body)
    {
        if ("GET".equals(method)) {
            return switch (path) {
                case "/_security/user" -> users.deepCopy();
                case "/_security/role" -> roles.deepCopy();
                case "/_security/role_mapping" -> mappings.deepCopy();
                default -> null;
            };
        }
        if (Reconciler.ROLES.equals(path) && body != null) {
            ObjectNode answer = JSON.createObjectNode();
            ObjectNode details = answer.putObject("errors").putObject("details");
            if ("POST".equals(method)) {
                body.path("roles").properties().forEach(role -> {
                    if (refused.contains(role.getKey())) {
                        details.putObject(role.getKey()).put("type", "illegal_argument_exception").put("reason", "refused " + role.getKey());
                    }
                    else {
                        roles.set(role.getKey(), role.getValue().deepCopy());
                    }
                });
            }
            else if ("DELETE".equals(method)) {
                body.path("names").forEach(name -> roles.remove(name.asText()));
            }
            ((ObjectNode) answer.path("errors")).put("count", details.size());
            return answer;
        }
        if (path.startsWith(USER) && "PUT".equals(method) && body != null) {
            String name = path.substring(USER.length());
            refuse(path, name);
            ObjectNode user = body.deepCopy();
            user.put("username", name);
            users.set(name, user);
            return json("{\"created\": false}");
        }
        if (path.startsWith(MAPPING)) {
            String name = path.substring(MAPPING.length());
            if ("PUT".equals(method) && body != null) {
                refuse(path, name);
                mappings.set(name, body.deepCopy());
                return json("{\"role_mapping\": {\"created\": true}}");
            }
            if ("DELETE".equals(method)) {
                if (mappings.remove(name) == null) {
                    throw new ElasticsearchFailure("DELETE " + path + ": 404 no such mapping", 404, null);
                }
                return json("{\"found\": true}");
            }
        }
        return null;
    }

    private void refuse(String path, String name)
    {
        if (refused.contains(name)) {
            throw new ElasticsearchFailure("PUT " + path + ": 400 refused " + name, 400, null);
        }
    }

    @Override
    public void close()
    {
        closed = true;
    }
}
