// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The indices and data streams of a cluster, which policies name, and the fields of their documents, read when they are
 * needed. The backing indices of data streams are named by their data stream, and indices whose names start with a dot,
 * hidden and system ones, are left out: policies reach them through patterns that start with a dot. Fields are the
 * paths of the values of documents, without objects, which hold fields, and without the multi-fields that index a
 * field once more, such as {@code name.keyword}: field level security shows or hides those with their field.
 */
final class Inventory
{
    /** The field types that hold other fields rather than values; a field without a type is an object. */
    private static final Set<String> CONTAINERS = Set.of("object", "nested");

    /** The inventory of a cluster that has nothing. */
    static final Inventory EMPTY = new Inventory("", Collections.emptySortedSet(), Map.of(), target -> Collections.emptySortedSet());

    private final String cluster;
    private final SortedSet<String> names;
    private final Map<String, String> streams;
    private final Function<String, SortedSet<String>> mappings;
    private final Map<String, SortedSet<String>> fields = new ConcurrentHashMap<>();

    /**
     * Creates the inventory.
     *
     * @param cluster the cluster's name
     * @param names the indices and data streams
     * @param streams the data stream of each backing index
     * @param mappings reads the fields of the indices an index name or pattern, or several separated by commas, reaches
     */
    Inventory(String cluster, SortedSet<String> names, Map<String, String> streams, Function<String, SortedSet<String>> mappings)
    {
        this.cluster = cluster;
        this.names = Collections.unmodifiableSortedSet(new TreeSet<>(names));
        this.streams = Map.copyOf(streams);
        this.mappings = mappings;
    }

    /**
     * Reads the inventory of a cluster.
     *
     * @param cluster the cluster's name
     * @param resolved what Elasticsearch's resolve API says of {@code *}
     * @param mappings reads the mappings of the indices a target reaches
     * @return the inventory
     */
    static Inventory of(String cluster, JsonNode resolved, Function<String, JsonNode> mappings)
    {
        SortedSet<String> names = new TreeSet<>();
        Map<String, String> streams = new HashMap<>();
        for (JsonNode index : resolved.path("indices")) {
            String name = index.path("name").asText("");
            String stream = index.path("data_stream").asText("");
            if (!stream.isEmpty()) {
                streams.put(name, stream);
            }
            else if (!name.isEmpty() && !name.startsWith(".")) {
                names.add(name);
            }
        }
        for (JsonNode stream : resolved.path("data_streams")) {
            String name = stream.path("name").asText("");
            if (!name.isEmpty() && !name.startsWith(".")) {
                names.add(name);
            }
        }
        return new Inventory(cluster, names, streams, target -> leaves(mappings.apply(target)));
    }

    /**
     * Reads the fields of the values of documents from a mappings answer.
     *
     * @param answer the mappings of indices, by index
     * @return the paths of their fields of values
     */
    static SortedSet<String> leaves(JsonNode answer)
    {
        SortedSet<String> leaves = new TreeSet<>();
        for (JsonNode index : answer) {
            walk("", index.path("mappings").path("properties"), leaves);
        }
        return leaves;
    }

    private static void walk(String prefix, JsonNode properties, Set<String> leaves)
    {
        Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String path = prefix + field.getKey();
            JsonNode definition = field.getValue();
            JsonNode inner = definition.path("properties");
            if (inner.isObject()) {
                walk(path + ".", inner, leaves);
            }
            else if (!CONTAINERS.contains(definition.path("type").asText("object"))) {
                leaves.add(path);
            }
        }
    }

    /**
     * Returns the cluster's name.
     *
     * @return it, empty before the first read
     */
    String cluster()
    {
        return cluster;
    }

    /**
     * Returns the indices and data streams.
     *
     * @return their names, sorted
     */
    SortedSet<String> names()
    {
        return names;
    }

    /**
     * Returns the name a policy knows an index by: the data stream of a backing index, or the index itself.
     *
     * @param index the index
     * @return the name
     */
    String named(String index)
    {
        return streams.getOrDefault(index, index);
    }

    /**
     * Returns the fields of the documents of what a target reaches, read once per inventory.
     *
     * @param target an index or data stream, a pattern, or several separated by commas
     * @return the paths of their fields of values
     */
    SortedSet<String> fields(String target)
    {
        return fields.computeIfAbsent(target, mappings);
    }
}
