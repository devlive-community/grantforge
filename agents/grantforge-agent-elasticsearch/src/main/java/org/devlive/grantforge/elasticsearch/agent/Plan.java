// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What a user is granted in Elasticsearch: the cluster privileges, and entries of indices and data streams, each with
 * its index privileges, the fields the user reads and the documents it sees, and why something a policy gives could
 * not be granted.
 *
 * @param cluster the cluster privileges
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
     * One entry of indices: the same privileges on each name and pattern it holds.
     *
     * @param indices the index names and patterns, which no other entry's overlap
     * @param privileges the index privileges
     * @param fields the fields the user reads, or none for every field
     * @param documents the query of the documents the user sees, or {@code null} for every document
     */
    record Entry(SortedSet<String> indices, SortedSet<String> privileges, SortedSet<String> fields, @Nullable String documents)
    {
        /** Copies the values. */
        Entry
        {
            indices = Collections.unmodifiableSortedSet(new TreeSet<>(indices));
            privileges = Collections.unmodifiableSortedSet(new TreeSet<>(privileges));
            fields = Collections.unmodifiableSortedSet(new TreeSet<>(fields));
        }

        /**
         * Returns the entry as Elasticsearch writes it. A field is granted with its multi-fields, such as
         * {@code name.keyword} with {@code name}, as field level security names them apart and they hold the same value.
         *
         * @return the entry's JSON, with sorted values
         */
        ObjectNode json()
        {
            ObjectNode entry = JSON.createObjectNode();
            array(entry.putArray("names"), indices);
            array(entry.putArray("privileges"), privileges);
            if (!fields.isEmpty()) {
                SortedSet<String> granted = new TreeSet<>();
                fields.forEach(field -> {
                    granted.add(field);
                    granted.add(field + ".*");
                });
                array(entry.putObject("field_security").putArray("grant"), granted);
            }
            if (documents != null) {
                entry.put("query", documents);
            }
            entry.put("allow_restricted_indices", false);
            return entry;
        }
    }

    /**
     * Returns whether the user is granted nothing, and so needs no role.
     *
     * @return {@code true} without cluster privileges or entries
     */
    boolean empty()
    {
        return cluster.isEmpty() && entries.isEmpty();
    }

    /**
     * Returns the plan as the role Elasticsearch keeps, marked as the agent's by its description and metadata.
     *
     * @param user the user the role is for
     * @return the role's JSON
     */
    ObjectNode role(String user)
    {
        ObjectNode role = JSON.createObjectNode();
        role.put("description", ServerState.MARKER + user);
        role.putObject("metadata").put(ServerState.OWNER, user);
        array(role.putArray("cluster"), cluster);
        ArrayNode indices = role.putArray("indices");
        entries.stream().map(Entry::json).sorted((first, second) -> first.toString().compareTo(second.toString())).forEach(indices::add);
        return role;
    }

    private static void array(ArrayNode array, SortedSet<String> values)
    {
        values.forEach(array::add);
    }
}
