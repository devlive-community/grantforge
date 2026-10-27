// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Snapshots of an Elasticsearch service as the server writes them, with the Elasticsearch plugin's levels, access types and
 * masks, built from policies the tests name. Principals are user names or {@code @group}; resources are level, value
 * pairs from the top; a value may hold several separated by {@code |}, and one starting with {@code !} names everything
 * but those.
 */
final class ElasticsearchSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode policies;
    private long nextId = 1;

    ElasticsearchSnapshots()
    {
        root.put("format", 1).put("service", "search").put("serviceType", "elasticsearch").put("serviceEnabled", true).put("policyVersion", 1);
        ObjectNode definition = root.putObject("definition");
        ArrayNode resources = definition.putArray("resources");
        level(resources, "index", null);
        level(resources, "field", "index");
        level(resources, "cluster", null);
        ArrayNode access = definition.putArray("accessTypes");
        for (String type : List.of("read", "index", "delete", "create_index", "delete_index", "view_index_metadata", "monitor", "cluster_monitor",
                "create_snapshot", "manage_index_templates", "manage_ingest_pipelines", "manage_ilm")) {
            access.addObject().put("name", type).putArray("impliedGrants");
        }
        access.addObject().put("name", "manage").putArray("impliedGrants").add("create_index").add("delete_index").add("view_index_metadata").add("monitor");
        access.addObject().put("name", "manage_slm").putArray("impliedGrants").add("create_snapshot");
        definition.putArray("conditions");
        ArrayNode masks = definition.putArray("maskTypes");
        masks.addObject().put("name", "mask").put("transformer", "{col}::/[A-Z]/::X::/[a-z]/::x::/[0-9]/::n");
        masks.addObject().put("name", "mask_show_last_4").put("transformer", "{col}::/.(?=.{4})/::x");
        masks.addObject().put("name", "mask_hash").put("transformer", "{col}::SHA-256");
        masks.addObject().put("name", "mask_hash_salted").put("transformer", "{col}");
        masks.addObject().put("name", "mask_none").putNull("transformer");
        masks.addObject().put("name", "custom").put("transformer", "{col}::{expr}");
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

    ElasticsearchSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    ElasticsearchSnapshots serviceType(String value)
    {
        root.put("serviceType", value);
        return this;
    }

    ElasticsearchSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    /** Adds a mask type with its transformer, or none. */
    ElasticsearchSnapshots maskType(String name, @Nullable String transformer)
    {
        ObjectNode mask = ((ArrayNode) root.path("definition").path("maskTypes")).addObject().put("name", name);
        if (transformer == null) {
            mask.putNull("transformer");
        }
        else {
            mask.put("transformer", transformer);
        }
        return this;
    }

    ElasticsearchSnapshots group(String group, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("groups")).putArray(group);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows principals the access types on a resource, given as level, value pairs. */
    ElasticsearchSnapshots allow(List<String> who, List<String> access, String... resource)
    {
        item(policy("ACCESS", resource).putArray("allow"), who, access);
        return this;
    }

    /** An access policy that denies principals the access types on a resource. */
    ElasticsearchSnapshots deny(List<String> who, List<String> access, String... resource)
    {
        ObjectNode document = policy("ACCESS", resource);
        document.putArray("allow");
        item(document.putArray("deny"), who, access);
        return this;
    }

    /** A masking policy that shows principals a field masked. */
    ElasticsearchSnapshots mask(List<String> who, String maskType, @Nullable String value, String index, String field)
    {
        ObjectNode item = item(policy("DATA_MASK", "index", index, "field", field).putArray("allow"), who, List.of("read")).put("maskType", maskType);
        if (value != null) {
            item.put("maskValue", value);
        }
        return this;
    }

    /** A row filtering policy that shows principals the documents of an index a query matches. */
    ElasticsearchSnapshots filter(List<String> who, String query, String index)
    {
        item(policy("ROW_FILTER", "index", index).putArray("allow"), who, List.of("read")).put("rowFilter", query);
        return this;
    }

    /** The id the next policy gets. */
    long nextId()
    {
        return nextId;
    }

    private ObjectNode policy(String type, String... resource)
    {
        ObjectNode policy = policies.addObject().put("id", Long.toString(nextId++)).put("name", "policy " + nextId).put("type", type)
                .put("priority", "NORMAL");
        ObjectNode document = policy.putObject("document");
        ObjectNode resources = document.putObject("resources");
        for (int index = 0; index < resource.length; index += 2) {
            String value = resource[index + 1];
            boolean excludes = value.startsWith("!");
            ArrayNode values = resources.putObject(resource[index]).put("excludes", excludes).put("recursive", false).putArray("values");
            for (String each : (excludes ? value.substring(1) : value).split("\\|", -1)) {
                values.add(each);
            }
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
