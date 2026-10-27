// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What a user is granted in OpenSearch: the cluster's action groups, and entries of indices and data streams, each with
 * its action groups, the fields the user reads, the fields it sees masked and the documents it sees, and why something a
 * policy gives could not be granted.
 *
 * @param cluster the cluster's action groups
 * @param entries the entries of indices
 * @param notes what could not be granted as the policies say, for the log
 */
record Plan(SortedSet<String> cluster, List<Entry> entries, List<String> notes)
{
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Copies the values. */
    Plan
    {
        cluster = Collections.unmodifiableSortedSet(new TreeSet<>(cluster));
        entries = List.copyOf(entries);
        notes = List.copyOf(notes);
    }

    /**
     * One entry of indices: the same permissions on each name and pattern it holds.
     *
     * @param indices the index names and patterns, which no other entry's overlap
     * @param actions the action groups
     * @param fields the fields the user reads, or none for every field
     * @param masked the masked fields, as the security plugin writes them
     * @param documents the query of the documents the user sees, or {@code null} for every document
     */
    record Entry(SortedSet<String> indices, SortedSet<String> actions, SortedSet<String> fields, SortedSet<String> masked, @Nullable String documents)
    {
        /** Copies the values. */
        Entry
        {
            indices = Collections.unmodifiableSortedSet(new TreeSet<>(indices));
            actions = Collections.unmodifiableSortedSet(new TreeSet<>(actions));
            fields = Collections.unmodifiableSortedSet(new TreeSet<>(fields));
            masked = Collections.unmodifiableSortedSet(new TreeSet<>(masked));
        }

        /**
         * Returns the entry as the security plugin writes it.
         *
         * @return the entry's JSON, with sorted values
         */
        ObjectNode json()
        {
            ObjectNode entry = JSON.createObjectNode();
            array(entry.putArray("index_patterns"), indices);
            array(entry.putArray("allowed_actions"), actions);
            array(entry.putArray("fls"), fields);
            array(entry.putArray("masked_fields"), masked);
            if (documents != null) {
                entry.put("dls", documents);
            }
            return entry;
        }
    }

    /**
     * Returns whether the user is granted nothing, and so needs no role.
     *
     * @return {@code true} without action groups or entries
     */
    boolean empty()
    {
        return cluster.isEmpty() && entries.isEmpty();
    }

    /**
     * Returns the plan as the role the security plugin keeps.
     *
     * @param user the user the role is for
     * @return the role's JSON
     */
    ObjectNode role(String user)
    {
        ObjectNode role = JSON.createObjectNode();
        role.put("description", ServerState.MARKER + user);
        array(role.putArray("cluster_permissions"), cluster);
        ArrayNode indices = role.putArray("index_permissions");
        entries.stream().map(Entry::json).sorted((first, second) -> first.toString().compareTo(second.toString())).forEach(indices::add);
        role.putArray("tenant_permissions");
        return role;
    }

    private static void array(ArrayNode array, SortedSet<String> values)
    {
        values.forEach(array::add);
    }
}
