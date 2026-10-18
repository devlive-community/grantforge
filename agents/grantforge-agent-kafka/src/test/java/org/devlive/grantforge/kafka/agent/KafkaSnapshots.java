// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.Snapshot;

import java.util.List;
import java.util.Map;

/**
 * Snapshots of a Kafka service as the server writes them, with the Kafka plugin's resources and access types, built from
 * policies the tests name. Principals are user names, {@code @group} or {@code +role}.
 */
final class KafkaSnapshots
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, List<String>> ACCESS = Map.ofEntries(Map.entry("read", List.of("describe")),
            Map.entry("write", List.of("describe")), Map.entry("create", List.of()), Map.entry("delete", List.of("describe")),
            Map.entry("alter", List.of("describe")), Map.entry("describe", List.of()), Map.entry("cluster_action", List.of()),
            Map.entry("describe_configs", List.of()), Map.entry("alter_configs", List.of("describe_configs")),
            Map.entry("idempotent_write", List.of()), Map.entry("create_tokens", List.of()), Map.entry("describe_tokens", List.of()));

    private final ObjectNode root = JSON.createObjectNode();
    private final ArrayNode resources;
    private final ArrayNode accessTypes;
    private final ArrayNode policies;
    private long nextId = 1;

    KafkaSnapshots()
    {
        root.put("format", 1).put("service", "events").put("serviceType", "kafka").put("serviceEnabled", true).put("policyVersion", 1);
        ObjectNode definition = root.putObject("definition");
        resources = definition.putArray("resources");
        for (String level : KafkaAccess.RESOURCES) {
            level(level);
        }
        accessTypes = definition.putArray("accessTypes");
        ACCESS.forEach((name, implied) -> accessType(name, implied.toArray(new String[0])));
        accessType("all", ACCESS.keySet().toArray(new String[0]));
        definition.putArray("conditions");
        policies = root.putArray("policies");
        root.putObject("roles");
        root.putObject("groups");
    }

    /** Adds a resource level the Kafka plugin does not have. */
    KafkaSnapshots level(String name)
    {
        resources.addObject().put("name", name).putNull("parent").put("matcher", "WILDCARD").put("caseSensitive", true);
        return this;
    }

    /** Adds an access type the Kafka plugin does not have. */
    KafkaSnapshots accessType(String name, String... implied)
    {
        ArrayNode grants = accessTypes.addObject().put("name", name).putArray("impliedGrants");
        for (String grant : implied) {
            grants.add(grant);
        }
        return this;
    }

    KafkaSnapshots version(long value)
    {
        root.put("policyVersion", value);
        return this;
    }

    KafkaSnapshots serviceType(String value)
    {
        root.put("serviceType", value);
        return this;
    }

    KafkaSnapshots disabled()
    {
        root.put("serviceEnabled", false);
        return this;
    }

    KafkaSnapshots group(String group, String... users)
    {
        ArrayNode members = ((ObjectNode) root.get("groups")).putArray(group);
        for (String user : users) {
            members.add(user);
        }
        return this;
    }

    /** An access policy that allows principals the access types on values of a level. */
    KafkaSnapshots allow(List<String> who, List<String> access, String level, String... values)
    {
        item(policy(level, false, values).putArray("allow"), who, access);
        return this;
    }

    /** An access policy that denies principals the access types on values of a level. */
    KafkaSnapshots deny(List<String> who, List<String> access, String level, String... values)
    {
        ObjectNode document = policy(level, false, values);
        document.putArray("allow");
        item(document.putArray("deny"), who, access);
        return this;
    }

    /**
     * Starts an access policy, for the tests to fill.
     *
     * @param level the resource level
     * @param excludes whether it covers every value but these
     * @param values the values
     * @return its document
     */
    ObjectNode policy(String level, boolean excludes, String... values)
    {
        ObjectNode policy = policies.addObject().put("id", Long.toString(nextId++)).put("name", "policy " + nextId).put("type", "ACCESS")
                .put("priority", "NORMAL");
        ObjectNode document = policy.putObject("document");
        ArrayNode list = document.putObject("resources").putObject(level).put("excludes", excludes).put("recursive", false).putArray("values");
        for (String value : values) {
            list.add(value);
        }
        return document;
    }

    /**
     * Adds an item.
     *
     * @param items the items
     * @param who the principals
     * @param access the access types
     * @return the item
     */
    static ObjectNode item(ArrayNode items, List<String> who, List<String> access)
    {
        ObjectNode item = items.addObject();
        ArrayNode users = item.putArray("users");
        ArrayNode groups = item.putArray("groups");
        ArrayNode roles = item.putArray("roles");
        for (String principal : who) {
            if (principal.startsWith("@")) {
                groups.add(principal.substring(1));
            }
            else if (principal.startsWith("+")) {
                roles.add(principal.substring(1));
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
        catch (com.fasterxml.jackson.core.JsonProcessingException broken) {
            throw new IllegalStateException(broken);
        }
    }

    Snapshot snapshot()
    {
        return Snapshot.parse(json(), Map.of());
    }
}
