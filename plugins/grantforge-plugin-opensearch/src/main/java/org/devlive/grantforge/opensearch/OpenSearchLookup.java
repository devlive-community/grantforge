// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.core5.net.URIBuilder;
import org.jspecify.annotations.Nullable;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * A lookup of the indices and data streams, fields or cluster whose names start with what was typed. Indices and data
 * streams come from OpenSearch's resolve API, without the backing indices of data streams, which policies name by
 * their data stream, and without hidden and system indices, whose names start with a dot, unless what was typed does. Fields come from the field
 * capabilities of the indices a policy names: the fields of documents, not OpenSearch's own metadata fields, objects,
 * or the multi-fields that index a field once more, such as {@code name.keyword}, which share their field's policies.
 *
 * @param resource the level: index, field or cluster
 * @param typed what the user typed
 * @param indices the index patterns a field lookup reads, or none for every index but those whose names start with a dot
 * @param limit the most values wanted
 */
record OpenSearchLookup(String resource, String typed, List<String> indices, int limit)
{
    /** The most index values a field lookup is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_INDICES = 100;

    /** What an index name may start with and hold, besides the wildcards a policy value may have. */
    private static final Pattern INDEX_TEXT = Pattern.compile("[^\\\\/\"<>|,# :+_-][^\\\\/\"<>|,# :]*|");
    /** The field types that hold other fields rather than values. */
    private static final Set<String> CONTAINERS = Set.of("object", "nested");

    /** Copies the indices. */
    OpenSearchLookup
    {
        indices = List.copyOf(indices);
    }

    /**
     * Builds the lookup of one level.
     *
     * @param resource the level
     * @param typed what the user typed
     * @param context the values the policy names for the other levels
     * @param limit the most values wanted
     * @return the lookup
     * @throws IllegalArgumentException for a level the service type does not have
     */
    static OpenSearchLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!List.of(OpenSearchProvider.INDEX, OpenSearchProvider.FIELD, OpenSearchProvider.CLUSTER).contains(resource)) {
            throw new IllegalArgumentException("OpenSearch services have no resource " + resource);
        }
        return new OpenSearchLookup(resource, typed.strip(), indices(context.get(OpenSearchProvider.INDEX)), limit);
    }

    /** The index patterns a policy names, none when it names none, any, too many or one OpenSearch could not read. */
    private static List<String> indices(@Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_INDICES) {
            return List.of();
        }
        // OpenSearch's index expressions know * but not ?, which becomes * to read a little more rather than nothing.
        List<String> patterns = values.stream().map(value -> value.strip().replace('?', '*')).toList();
        if (patterns.stream().anyMatch(value -> "*".equals(value) || value.isEmpty() || !INDEX_TEXT.matcher(value).matches())) {
            return List.of();
        }
        return patterns;
    }

    /**
     * Runs the lookup.
     *
     * @param client the cluster's client
     * @return the names, sorted, at most {@link #limit} of them
     */
    List<String> run(OpenSearchClient client)
    {
        SortedSet<String> names = switch (resource) {
            case OpenSearchProvider.INDEX -> indices(client);
            case OpenSearchProvider.FIELD -> fields(client);
            default -> cluster(client);
        };
        return names.stream().limit(limit).toList();
    }

    private SortedSet<String> indices(OpenSearchClient client)
    {
        SortedSet<String> names = new TreeSet<>();
        if (!INDEX_TEXT.matcher(typed).matches()) {
            return names;
        }
        String prefix = typed.replace('?', '*');
        JsonNode resolved = client.get(path("_resolve", "index", prefix + "*"), Map.of("expand_wildcards", typed.startsWith(".") ? "all" : "open,closed"));
        for (JsonNode index : resolved.path("indices")) {
            if (index.path("data_stream").isMissingNode()) {
                names.add(index.path("name").asText());
            }
        }
        for (JsonNode stream : resolved.path("data_streams")) {
            names.add(stream.path("name").asText());
        }
        names.removeIf(name -> name.isEmpty() || name.startsWith(".") && !typed.startsWith("."));
        return names;
    }

    // A set of types per field.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private SortedSet<String> fields(OpenSearchClient client)
    {
        String target = indices.isEmpty() ? "*,-.*" : String.join(",", indices);
        JsonNode capabilities = client.get(path(target, "_field_caps"), Map.of("fields", "*", "ignore_unavailable", "true", "allow_no_indices", "true",
                "expand_wildcards", "open,closed")).path("fields");
        Map<String, Set<String>> types = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = capabilities.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            Set<String> those = new TreeSet<>();
            entry.getValue().fieldNames().forEachRemaining(those::add);
            types.put(entry.getKey(), those);
        }
        SortedSet<String> names = new TreeSet<>();
        types.forEach((name, those) -> {
            if (!name.startsWith("_") && name.startsWith(typed) && !CONTAINERS.containsAll(those) && !multiField(name, types)) {
                names.add(name);
            }
        });
        return names;
    }

    /**
     * Returns whether a field indexes another field once more, such as {@code name.keyword}: its parent is a field of
     * values, not an object.
     *
     * @param name the field's path
     * @param types the types of every field, by path
     * @return {@code true} for a multi-field
     */
    static boolean multiField(String name, Map<String, Set<String>> types)
    {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return false;
        }
        Set<String> parent = types.get(name.substring(0, dot));
        return parent != null && !CONTAINERS.containsAll(parent);
    }

    private SortedSet<String> cluster(OpenSearchClient client)
    {
        SortedSet<String> names = new TreeSet<>();
        String name = client.get("/", Map.of()).path("cluster_name").asText("");
        if (!name.isEmpty() && name.startsWith(typed)) {
            names.add(name);
        }
        return names;
    }

    /**
     * Builds an encoded path from its segments.
     *
     * @param segments the segments, such as an index pattern and an API's name
     * @return the path
     */
    static String path(String... segments)
    {
        try {
            return new URIBuilder().setPathSegments(segments).build().getRawPath();
        }
        catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("no path of " + List.of(segments), impossible);
        }
    }
}
