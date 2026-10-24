// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Snapshots of a Pulsar service as the server writes them, with the Pulsar plugin's levels, access types and its
 * {@code ip-range} condition, built from policies the tests name. Principals are user names or {@code @group}; resources
 * are level, value pairs from the top.
 */
final class PulsarSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode policies;
    private long nextId = 1;

    PulsarSnapshots()
    {
        root.put("format", 1).put("service", "events").put("serviceType", "pulsar").put("serviceEnabled", true).put("policyVersion", 1);
        ObjectNode definition = root.putObject("definition");
        ArrayNode resources = definition.putArray("resources");
        level(resources, "tenant", null);
        level(resources, "namespace", "tenant");
        level(resources, "topic", "namespace");
        level(resources, "subscription", "topic");
        level(resources, "cluster", null);
        ArrayNode access = definition.putArray("accessTypes");
        for (String plain : List.of("produce", "consume", "functions", "sources", "sinks", "packages")) {
            access.addObject().put("name", plain).putArray("impliedGrants");
        }
        access.addObject().put("name", "admin_topic").putArray("impliedGrants").add("produce").add("consume");
        access.addObject().put("name", "admin_namespace").putArray("impliedGrants").add("admin_topic");
        ArrayNode tenant = access.addObject().put("name", "admin_tenant").putArray("impliedGrants");
        List.of("admin_namespace", "functions", "sources", "sinks", "packages").forEach(tenant::add);
        access.addObject().put("name", "admin").putArray("impliedGrants").add("admin_tenant");
        definition.putArray("conditions").addObject().put("name", "ip-range").put("evaluator", "ip-range");
        policies = root.putArray("policies");
        root.putObject("roles");
        root.putObject("groups");
    }

    private static void level(ArrayNode resources, String name, @Nullable String parent)
    {
        ObjectNode level = resources.addObject().put("name", name).put("matcher", "WILDCARD").put("caseSensitive", true);
        if (parent == null) {
            level.putNull("parent");
        }
        else {
            level.put("parent", parent);
        }
    }

    PulsarSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    PulsarSnapshots serviceType(String value)
    {
        root.put("serviceType", value);
        return this;
    }

    PulsarSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    PulsarSnapshots group(String group, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("groups")).putArray(group);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows principals the access types on a resource, given as level, value pairs. */
    PulsarSnapshots allow(List<String> who, List<String> access, String... resource)
    {
        item(policy(resource).putArray("allow"), who, access, List.of());
        return this;
    }

    /** An access policy that allows principals the access types on a resource from some client addresses only. */
    PulsarSnapshots allowFrom(List<String> addresses, List<String> who, List<String> access, String... resource)
    {
        item(policy(resource).putArray("allow"), who, access, addresses);
        return this;
    }

    /** An access policy that denies principals the access types on a resource. */
    PulsarSnapshots deny(List<String> who, List<String> access, String... resource)
    {
        ObjectNode document = policy(resource);
        document.putArray("allow");
        item(document.putArray("deny"), who, access, List.of());
        return this;
    }

    private ObjectNode policy(String... resource)
    {
        ObjectNode policy = policies.addObject().put("id", Long.toString(nextId++)).put("name", "policy " + nextId).put("type", "ACCESS")
                .put("priority", "NORMAL");
        ObjectNode document = policy.putObject("document");
        ObjectNode resources = document.putObject("resources");
        for (int index = 0; index < resource.length; index += 2) {
            resources.putObject(resource[index]).put("excludes", false).put("recursive", false).putArray("values").add(resource[index + 1]);
        }
        return document;
    }

    private static void item(ArrayNode items, List<String> who, List<String> access, List<String> addresses)
    {
        ObjectNode item = items.addObject();
        ArrayNode users = item.putArray("users");
        ArrayNode groups = item.putArray("groups");
        item.putArray("roles");
        for (String principal : who) {
            if (principal.startsWith("@")) {
                groups.add(principal.substring(1));
            }
            else {
                users.add(principal);
            }
        }
        ArrayNode types = item.putArray("accessTypes");
        access.forEach(types::add);
        if (!addresses.isEmpty()) {
            ArrayNode values = item.putArray("conditions").addObject().put("type", "ip-range").putArray("values");
            addresses.forEach(values::add);
        }
    }

    byte[] json()
    {
        try {
            return JSON.writeValueAsBytes(root);
        }
        catch (JsonProcessingException broken) {
            throw new IllegalStateException(broken);
        }
    }

    Snapshot snapshot()
    {
        return Snapshot.parse(json(), PulsarAgentRuntime.EVALUATORS);
    }
}
