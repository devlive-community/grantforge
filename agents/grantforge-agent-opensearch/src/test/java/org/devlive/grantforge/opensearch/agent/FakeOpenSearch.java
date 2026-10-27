// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.core.JsonPointer;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * An OpenSearch cluster in memory: the security plugin's internal users, roles and role mappings, which JSON patches
 * change as the security plugin applies them, all at once or not at all, and answers set for other requests, such as
 * the resolve API, mappings, the validate API and searches of the audit log. Requests it has no answer for fail with 404;
 * failures may be set per request.
 */
final class FakeOpenSearch
        implements OpenSearch
{
    private static final ObjectMapper JSON = new ObjectMapper();

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
    private final Map<String, OpenSearchFailure> failures = new HashMap<>();
    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Set<String> refused = new HashSet<>();
    private boolean closed;

    FakeOpenSearch()
    {
        answer("GET /", json("{\"cluster_name\": \"search\", \"version\": {\"distribution\": \"opensearch\", \"number\": \"3.9.0\"}}"));
        answer("GET /_plugins/_security/authinfo", json("{\"user_name\": \"agent\"}"));
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
    FakeOpenSearch answer(String request, JsonNode answer)
    {
        answers.put(request, body -> answer);
        return this;
    }

    /** Makes a request, named as {@code METHOD path}, fail. */
    FakeOpenSearch fail(String request, int status)
    {
        failures.put(request, new OpenSearchFailure(request + ": " + status + " refused", status, null));
        return this;
    }

    /** Lets a request that was set to fail succeed again. */
    FakeOpenSearch heal(String request)
    {
        failures.remove(request);
        return this;
    }

    /** Makes the security plugin refuse every patch that writes a role or mapping of a name. */
    FakeOpenSearch refuse(String name)
    {
        refused.add(name);
        return this;
    }

    /** Adds an internal user. */
    FakeOpenSearch user(String name, String... backendRoles)
    {
        ObjectNode user = users.putObject(name);
        user.put("reserved", false).put("hidden", false);
        for (String role : backendRoles) {
            user.withArray("backend_roles").add(role);
        }
        user.putArray("opendistro_security_roles");
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
        OpenSearchFailure failure = failures.get(request);
        if (failure != null) {
            throw failure;
        }
        ObjectNode config = switch (path) {
            case Reconciler.ROLES -> roles;
            case Reconciler.MAPPINGS -> mappings;
            case "/_plugins/_security/api/internalusers" -> users;
            default -> null;
        };
        if (config != null && "GET".equals(method)) {
            return config.deepCopy();
        }
        if (config != null && "PATCH".equals(method) && body != null) {
            patch(config, body, refused);
            return json("{\"status\": \"OK\"}");
        }
        Function<@Nullable JsonNode, JsonNode> answer = answers.get(request);
        if (answer == null) {
            throw new OpenSearchFailure(request + ": 404 no such resource", 404, null);
        }
        return answer.apply(body);
    }

    /** Applies a JSON patch whole, as the security plugin does, refusing it whole when a change names no role. */
    private static void patch(ObjectNode config, JsonNode operations, Set<String> refused)
    {
        ObjectNode changed = config.deepCopy();
        for (JsonNode operation : operations) {
            String name = JsonPointer.compile(operation.path("path").asText()).getMatchingProperty();
            if ("add".equals(operation.path("op").asText())) {
                if (refused.contains(name)) {
                    throw new OpenSearchFailure("PATCH: 400 refused " + name, 400, null);
                }
                changed.set(name, operation.path("value").deepCopy());
            }
            else {
                changed.remove(name);
            }
        }
        config.removeAll();
        config.setAll(changed);
    }

    @Override
    public void close()
    {
        closed = true;
    }
}
