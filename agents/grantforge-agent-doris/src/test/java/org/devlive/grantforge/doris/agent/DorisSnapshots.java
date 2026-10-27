// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Snapshots of a Doris service as the server writes them, with the Doris plugin's levels and access types,
 * built from policies the tests name. Principals are user names or {@code @group}; resources are level, value pairs from
 * the top.
 */
final class DorisSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode policies;
    private long nextId = 1;

    DorisSnapshots()
    {
        root.put("format", 1).put("service", "warehouse").put("serviceType", "doris").put("serviceEnabled", true).put("policyVersion", 1);
        ObjectNode definition = root.putObject("definition");
        ArrayNode resources = definition.putArray("resources");
        level(resources, "catalog", null, true);
        level(resources, "database", "catalog", true);
        level(resources, "table", "database", true);
        level(resources, "column", "table", false);
        level(resources, "workload_group", null, true);
        ArrayNode access = definition.putArray("accessTypes");
        for (String type : List.of("select", "load", "alter", "drop", "show_view", "create", "drop_database", "create_database", "usage")) {
            access.addObject().put("name", type).putArray("impliedGrants");
        }
        definition.putArray("conditions");
        policies = root.putArray("policies");
        root.putObject("roles");
        root.putObject("groups");
    }

    private static void level(ArrayNode resources, String name, @Nullable String parent, boolean caseSensitive)
    {
        ObjectNode level = resources.addObject().put("name", name).put("matcher", "WILDCARD").put("caseSensitive", caseSensitive);
        if (parent == null) {
            level.putNull("parent");
        }
        else {
            level.put("parent", parent);
        }
    }

    DorisSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    DorisSnapshots serviceType(String value)
    {
        root.put("serviceType", value);
        return this;
    }

    DorisSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    DorisSnapshots group(String group, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("groups")).putArray(group);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows principals the access types on a resource, given as level, value pairs. */
    DorisSnapshots allow(List<String> who, List<String> access, String... resource)
    {
        item(policy("ACCESS", "NORMAL", resource).putArray("allow"), who, access);
        return this;
    }

    /** An access policy that denies principals the access types on a resource. */
    DorisSnapshots deny(List<String> who, List<String> access, String... resource)
    {
        ObjectNode document = policy("ACCESS", "NORMAL", resource);
        document.putArray("allow");
        item(document.putArray("deny"), who, access);
        return this;
    }

    /** A row filtering policy that shows principals the rows of a table a condition holds for. */
    DorisSnapshots filter(List<String> who, String condition, String database, String table)
    {
        item(policy("ROW_FILTER", "NORMAL", "catalog", "internal", "database", database, "table", table).putArray("allow"), who, List.of("select"))
                .put("rowFilter", condition);
        return this;
    }

    /** The id the next policy gets. */
    long nextId()
    {
        return nextId;
    }

    private ObjectNode policy(String type, String priority, String... resource)
    {
        ObjectNode policy = policies.addObject().put("id", Long.toString(nextId++)).put("name", "policy " + nextId).put("type", type)
                .put("priority", priority);
        ObjectNode document = policy.putObject("document");
        ObjectNode resources = document.putObject("resources");
        for (int index = 0; index < resource.length; index += 2) {
            resources.putObject(resource[index]).put("excludes", false).put("recursive", false).putArray("values").add(resource[index + 1]);
        }
        return document;
    }

    private static ObjectNode item(ArrayNode items, List<String> who, List<String> access)
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
        return item;
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
        return Snapshot.parse(json(), Map.of());
    }
}
