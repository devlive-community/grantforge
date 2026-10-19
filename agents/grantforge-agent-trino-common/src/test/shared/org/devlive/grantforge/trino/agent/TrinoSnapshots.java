// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Snapshots of a Trino service as the server writes them, with the Trino plugin's levels, access types and masks, built
 * from policies the tests name. Principals are user names or {@code @group}; resources are level, value pairs from the
 * top.
 */
final class TrinoSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> DATA = List.of("select", "insert", "update", "delete", "create", "drop", "alter");

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode policies;
    private long nextId = 1;

    TrinoSnapshots()
    {
        root.put("format", 1).put("service", "warehouse").put("serviceType", "trino").put("serviceEnabled", true).put("policyVersion", 1);
        ObjectNode definition = root.putObject("definition");
        ArrayNode resources = definition.putArray("resources");
        level(resources, "catalog", null, false);
        level(resources, "schema", "catalog", false);
        level(resources, "table", "schema", false);
        level(resources, "column", "table", false);
        level(resources, "function", "schema", false);
        level(resources, "procedure", "schema", false);
        level(resources, "session_property", "catalog", false);
        level(resources, "system_session_property", null, false);
        level(resources, "user", null, true);
        level(resources, "system_information", null, true);
        ArrayNode access = definition.putArray("accessTypes");
        for (String type : List.of("select", "insert", "update", "delete", "create", "drop", "alter", "execute", "create_function", "drop_function",
                "set", "impersonate", "view_query", "kill_query", "read", "write")) {
            access.addObject().put("name", type).putArray("impliedGrants");
        }
        ArrayNode all = access.addObject().put("name", "all").putArray("impliedGrants");
        DATA.forEach(all::add);
        definition.putArray("conditions");
        ArrayNode masks = definition.putArray("maskTypes");
        masks.addObject().put("name", "mask").put("transformer",
                "regexp_replace(regexp_replace(regexp_replace({col}, '[A-Z]', 'X'), '[a-z]', 'x'), '[0-9]', 'n')");
        masks.addObject().put("name", "mask_hash").put("transformer", "to_hex(sha256(to_utf8({col})))");
        masks.addObject().put("name", "mask_null").put("transformer", "NULL");
        masks.addObject().put("name", "mask_none").put("transformer", "{col}");
        masks.addObject().put("name", "mask_date_show_year").put("transformer", "date_trunc('year', {col})");
        masks.addObject().put("name", "custom").put("transformer", "{expr}");
        masks.addObject().put("name", "opaque").putNull("transformer");
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

    TrinoSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    TrinoSnapshots serviceType(String value)
    {
        root.put("serviceType", value);
        return this;
    }

    TrinoSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    TrinoSnapshots group(String group, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("groups")).putArray(group);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows principals the access types on a resource, given as level, value pairs. */
    TrinoSnapshots allow(List<String> who, List<String> access, String... resource)
    {
        item(policy("ACCESS", resource).putArray("allow"), who, access);
        return this;
    }

    /** An access policy that denies principals the access types on a resource. */
    TrinoSnapshots deny(List<String> who, List<String> access, String... resource)
    {
        ObjectNode document = policy("ACCESS", resource);
        document.putArray("allow");
        item(document.putArray("deny"), who, access);
        return this;
    }

    /** A masking policy on a column. */
    TrinoSnapshots mask(List<String> who, String maskType, @Nullable String value, String... resource)
    {
        ObjectNode item = item(policy("DATA_MASK", resource).putArray("allow"), who, List.of("select")).put("maskType", maskType);
        if (value != null) {
            item.put("maskValue", value);
        }
        return this;
    }

    /** A row filtering policy on a table. */
    TrinoSnapshots filter(List<String> who, String condition, String... resource)
    {
        item(policy("ROW_FILTER", resource).putArray("allow"), who, List.of("select")).put("rowFilter", condition);
        return this;
    }

    private ObjectNode policy(String type, String... resource)
    {
        ObjectNode policy = policies.addObject().put("id", Long.toString(nextId++)).put("name", type + " " + nextId).put("type", type)
                .put("priority", "NORMAL");
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
