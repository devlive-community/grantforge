// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.NamedList;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Lists the collections, or the request paths of the collections a policy names, that start with what was typed. In
 * SolrCloud the collections are the cluster's; a node that runs without SolrCloud has cores instead, which are named
 * as collections. A collection's paths are the request handlers it registers, such as {@code /select} and
 * {@code /update}. The cluster level has its one value.
 *
 * @param resource the level
 * @param typed what was typed
 * @param collections the collection values the policy names, or none for every collection
 * @param limit the most names returned
 */
record SolrLookup(String resource, String typed, List<String> collections, int limit)
{
    /** The most collections whose paths one lookup asks for. */
    static final int MAX_ASKED = 20;

    /**
     * Builds the lookup of one level.
     *
     * @param resource the level
     * @param typed what the user typed
     * @param context the values the policy names for the other levels
     * @param limit the most names returned
     * @return the lookup
     * @throws IllegalArgumentException for a level the service type does not have
     */
    static SolrLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!List.of(SolrProvider.COLLECTION, SolrProvider.PATH, SolrProvider.CLUSTER).contains(resource)) {
            throw new IllegalArgumentException("Solr services look up collections, paths and the cluster, not " + resource);
        }
        List<String> values = context.get(SolrProvider.COLLECTION);
        return new SolrLookup(resource, typed.strip(), values == null ? List.of() : values.stream().map(String::strip).toList(), limit);
    }

    /**
     * Turns a policy's wildcard into a pattern of names.
     *
     * @param wildcard the value, with {@code *} for any run of characters and {@code ?} for one
     * @return the pattern
     */
    static Pattern pattern(String wildcard)
    {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < wildcard.length(); index++) {
            char character = wildcard.charAt(index);
            regex.append(character == '*' ? ".*" : character == '?' ? "." : Pattern.quote(String.valueOf(character)));
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    /**
     * Runs the lookup.
     *
     * @param node the node to ask
     * @return the names, sorted, at most {@link #limit()}
     * @throws SolrServerException when the node cannot be asked
     * @throws IOException when a request fails on the way
     */
    List<String> run(SolrNodeClient.Node node) throws SolrServerException, IOException
    {
        SortedSet<String> names = new TreeSet<>();
        if (SolrProvider.CLUSTER.equals(resource)) {
            names.add(SolrProvider.CLUSTER_VALUE);
        }
        else if (SolrProvider.COLLECTION.equals(resource)) {
            names.addAll(collections(node));
        }
        else {
            List<Pattern> patterns = collections.stream().filter(value -> !"*".equals(value)).map(SolrLookup::pattern).toList();
            List<String> named = new ArrayList<>();
            for (String collection : collections(node)) {
                if (named.size() < MAX_ASKED && (patterns.isEmpty() || patterns.stream().anyMatch(pattern -> pattern.matcher(collection).matches()))) {
                    named.add(collection);
                }
            }
            for (String collection : named) {
                names.addAll(paths(node, collection));
            }
        }
        List<String> first = new ArrayList<>();
        for (String name : names) {
            if (first.size() < limit && name.startsWith(typed)) {
                first.add(name);
            }
        }
        return first;
    }

    /**
     * Returns the collections of the cluster, or the cores of a node that runs without SolrCloud.
     *
     * @param node the node to ask
     * @return their names
     * @throws SolrServerException when the node cannot be asked
     * @throws IOException when a request fails on the way
     */
    static List<String> collections(SolrNodeClient.Node node) throws SolrServerException, IOException
    {
        if (cloud(node)) {
            return names(node.get("/admin/collections", params("action", "LIST"), null).get("collections"));
        }
        return names(node.get("/admin/cores", params("action", "STATUS", "indexInfo", "false"), null).get("status"));
    }

    /**
     * Returns whether a node runs in SolrCloud mode.
     *
     * @param node the node to ask
     * @return {@code true} if it does
     * @throws SolrServerException when the node cannot be asked
     * @throws IOException when a request fails on the way
     */
    static boolean cloud(SolrNodeClient.Node node) throws SolrServerException, IOException
    {
        return "solrcloud".equals(String.valueOf(system(node).get("mode")));
    }

    /**
     * Returns what a node says of itself.
     *
     * @param node the node to ask
     * @return its system information, with {@code mode} and {@code lucene/solr-spec-version}
     * @throws SolrServerException when the node cannot be asked
     * @throws IOException when a request fails on the way
     */
    static NamedList<Object> system(SolrNodeClient.Node node) throws SolrServerException, IOException
    {
        return node.get("/admin/info/system", params(), null);
    }

    /** The request paths a collection registers; none when its configuration may not be read. */
    private static List<String> paths(SolrNodeClient.Node node, String collection) throws SolrServerException, IOException
    {
        try {
            Object config = node.get("/config/requestHandler", params(), collection).get("config");
            return names(config instanceof NamedList<?> list ? list.get("requestHandler") : config instanceof Map<?, ?> map ? map.get("requestHandler") : null);
        }
        catch (SolrException refused) {
            if (refused.code() == 401 || refused.code() == 403 || refused.code() == 404) {
                return List.of();
            }
            throw refused;
        }
    }

    /** The names of a list of names, or the keys of a map or named list. */
    private static List<String> names(@Nullable Object value)
    {
        List<String> names = new ArrayList<>();
        if (value instanceof NamedList<?> list) {
            for (Map.Entry<String, ?> entry : list) {
                names.add(entry.getKey());
            }
        }
        else if (value instanceof Map<?, ?> map) {
            map.keySet().forEach(key -> names.add(String.valueOf(key)));
        }
        else if (value instanceof Collection<?> list) {
            list.forEach(name -> names.add(String.valueOf(name)));
        }
        return names;
    }

    private static ModifiableSolrParams params(String... pairs)
    {
        ModifiableSolrParams params = new ModifiableSolrParams();
        for (int index = 0; index < pairs.length; index += 2) {
            params.set(pairs[index], pairs[index + 1]);
        }
        return params;
    }
}
