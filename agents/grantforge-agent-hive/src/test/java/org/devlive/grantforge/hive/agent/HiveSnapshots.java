// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Snapshots of a Hive service as the server writes them, with the Hive plugin's resources, access types and masks, built
 * from policies the tests name.
 */
final class HiveSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ACCESS = List.of("select", "update", "create", "drop", "alter", "index", "lock", "read", "write",
            "repladmin", "serviceadmin", "tempudfadmin", "refresh");

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode policies;
    private long nextId = 1;

    HiveSnapshots()
    {
        root.put("format", 1).put("service", "warehouse").put("serviceType", "hive").put("serviceEnabled", true).put("policyVersion", 7);
        ObjectNode definition = root.putObject("definition");
        ArrayNode resources = definition.putArray("resources");
        level(resources, "database", null, "WILDCARD", false);
        level(resources, "table", "database", "WILDCARD", false);
        level(resources, "udf", "database", "WILDCARD", false);
        level(resources, "column", "table", "WILDCARD", false);
        level(resources, "url", null, "PATH", true);
        level(resources, "hiveservice", null, "WILDCARD", false);
        level(resources, "global", null, "WILDCARD", false);
        ArrayNode access = definition.putArray("accessTypes");
        for (String type : ACCESS) {
            access.addObject().put("name", type).putArray("impliedGrants");
        }
        ArrayNode all = access.addObject().put("name", "all").putArray("impliedGrants");
        ACCESS.stream().filter(type -> !"tempudfadmin".equals(type)).forEach(all::add);
        definition.putArray("conditions");
        ArrayNode masks = definition.putArray("maskTypes");
        masks.addObject().put("name", "mask_hash").put("transformer", "mask_hash({col})");
        masks.addObject().put("name", "mask_null").put("transformer", "NULL");
        masks.addObject().put("name", "mask_none").put("transformer", "{col}");
        masks.addObject().put("name", "custom").put("transformer", "{expr}");
        masks.addObject().put("name", "opaque").putNull("transformer");
        policies = root.putArray("policies");
        root.putObject("roles");
        root.putObject("groups");
    }

    private static void level(ArrayNode resources, String name, @Nullable String parent, String matcher, boolean caseSensitive)
    {
        ObjectNode level = resources.addObject().put("name", name).put("matcher", matcher).put("caseSensitive", caseSensitive);
        if (parent == null) {
            level.putNull("parent");
        }
        else {
            level.put("parent", parent);
        }
    }

    HiveSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    HiveSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    HiveSnapshots role(String role, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("roles")).putArray(role);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows users the access types on a resource, given as level, value pairs. */
    HiveSnapshots allow(List<String> users, List<String> access, String... resource)
    {
        ObjectNode document = policy("ACCESS", resource);
        item(document.putArray("allow"), users, access);
        return this;
    }

    /** An access policy that denies users the access types on a resource. */
    HiveSnapshots deny(List<String> users, List<String> access, String... resource)
    {
        ObjectNode document = policy("ACCESS", resource);
        document.putArray("allow");
        item(document.putArray("deny"), users, access);
        return this;
    }

    /** A masking policy on a column. */
    HiveSnapshots mask(List<String> users, String maskType, @Nullable String value, String... resource)
    {
        ObjectNode item = item(policy("DATA_MASK", resource).putArray("allow"), users, List.of("select")).put("maskType", maskType);
        if (value != null) {
            item.put("maskValue", value);
        }
        return this;
    }

    /** A row filtering policy on a table. */
    HiveSnapshots filter(List<String> users, String condition, String... resource)
    {
        item(policy("ROW_FILTER", resource).putArray("allow"), users, List.of("select")).put("rowFilter", condition);
        return this;
    }

    private ObjectNode policy(String type, String... resource)
    {
        ObjectNode policy = policies.addObject().put("id", Long.toString(nextId++)).put("name", type + " " + nextId).put("type", type)
                .put("priority", "NORMAL");
        ObjectNode document = policy.putObject("document");
        ObjectNode resources = document.putObject("resources");
        for (int index = 0; index < resource.length; index += 2) {
            ObjectNode values = resources.putObject(resource[index]).put("excludes", false).put("recursive", "url".equals(resource[index]));
            values.putArray("values").add(resource[index + 1]);
        }
        return document;
    }

    private static ObjectNode item(ArrayNode items, List<String> users, List<String> access)
    {
        ObjectNode item = items.addObject();
        ArrayNode names = item.putArray("users");
        ArrayNode groups = item.putArray("groups");
        for (String user : users) {
            if (user.startsWith("@")) {
                groups.add(user.substring(1));
            }
            else {
                names.add(user);
            }
        }
        item.putArray("roles");
        ArrayNode types = item.putArray("accessTypes");
        access.forEach(types::add);
        return item;
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

    Policies policies()
    {
        return Policies.of(snapshot());
    }
}
