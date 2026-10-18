// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Snapshots of an HBase service as the server writes them, with the HBase plugin's levels and access types, built from
 * policies the tests name. Principals are user names or {@code @group}; resources are level, value pairs from the top.
 */
final class HBaseSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode policies;
    private long nextId = 1;

    HBaseSnapshots()
    {
        root.put("format", 1).put("service", "tables").put("serviceType", "hbase").put("serviceEnabled", true).put("policyVersion", 1);
        ObjectNode definition = root.putObject("definition");
        ArrayNode resources = definition.putArray("resources");
        level(resources, "global", null);
        level(resources, "namespace", null);
        level(resources, "table", "namespace");
        level(resources, "column_family", "table");
        level(resources, "column", "column_family");
        ArrayNode access = definition.putArray("accessTypes");
        for (String type : List.of("read", "write", "create", "admin")) {
            access.addObject().put("name", type).putArray("impliedGrants");
        }
        definition.putArray("conditions");
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

    HBaseSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    HBaseSnapshots serviceType(String value)
    {
        root.put("serviceType", value);
        return this;
    }

    HBaseSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    HBaseSnapshots group(String group, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("groups")).putArray(group);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows principals the access types on a resource, given as level, value pairs. */
    HBaseSnapshots allow(List<String> who, List<String> access, String... resource)
    {
        item(policy(resource).putArray("allow"), who, access);
        return this;
    }

    /** An access policy that denies principals the access types on a resource. */
    HBaseSnapshots deny(List<String> who, List<String> access, String... resource)
    {
        ObjectNode document = policy(resource);
        document.putArray("allow");
        item(document.putArray("deny"), who, access);
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

    private static void item(ArrayNode items, List<String> who, List<String> access)
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
    }

    byte[] json()
    {
        try {
            return JSON.writeValueAsBytes(root);
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException broken) {
            throw new IllegalStateException(broken);
        }
    }

    Snapshot snapshot()
    {
        return Snapshot.parse(json(), Map.of());
    }
}
