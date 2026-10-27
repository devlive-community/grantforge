// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Snapshots of a PostgreSQL service as the server writes them, with the PostgreSQL plugin's levels and access types,
 * built from policies the tests name. Principals are user names or {@code @group}; resources are level, value pairs from
 * the top.
 */
final class PostgreSqlSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode policies;
    private long nextId = 1;

    PostgreSqlSnapshots()
    {
        root.put("format", 1).put("service", "warehouse").put("serviceType", "postgresql").put("serviceEnabled", true).put("policyVersion", 1);
        ObjectNode definition = root.putObject("definition");
        ArrayNode resources = definition.putArray("resources");
        level(resources, "database", null);
        level(resources, "schema", "database");
        level(resources, "table", "schema");
        level(resources, "column", "table");
        ArrayNode access = definition.putArray("accessTypes");
        for (String type : List.of("select", "insert", "update", "delete", "truncate", "trigger", "create", "create_schema", "temporary")) {
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

    PostgreSqlSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    PostgreSqlSnapshots serviceType(String value)
    {
        root.put("serviceType", value);
        return this;
    }

    PostgreSqlSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    PostgreSqlSnapshots group(String group, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("groups")).putArray(group);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows principals the access types on a resource, given as level, value pairs. */
    PostgreSqlSnapshots allow(List<String> who, List<String> access, String... resource)
    {
        item(policy("ACCESS", "NORMAL", resource).putArray("allow"), who, access);
        return this;
    }

    /** An access policy that denies principals the access types on a resource. */
    PostgreSqlSnapshots deny(List<String> who, List<String> access, String... resource)
    {
        ObjectNode document = policy("ACCESS", "NORMAL", resource);
        document.putArray("allow");
        item(document.putArray("deny"), who, access);
        return this;
    }

    /** A row filtering policy that shows principals the rows of a table a condition holds for. */
    PostgreSqlSnapshots filter(List<String> who, String condition, String database, String schema, String table)
    {
        item(policy("ROW_FILTER", "NORMAL", "database", database, "schema", schema, "table", table).putArray("allow"), who, List.of("select"))
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
