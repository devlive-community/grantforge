// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Solr asks about, as the levels of the Solr service type name it: a collection, a request path within it, or the
 * cluster. A request to a collection is decided at the collection and at the collection with its path, such as
 * {@code /select}, so that a policy can allow a collection and deny one of its handlers; the cluster's administrators may
 * do everything to it.
 */
final class SolrResource
{
    static final String COLLECTION = "collection";
    static final String PATH = "path";
    static final String CLUSTER = "cluster";

    /** The one value of the cluster level. */
    static final String CLUSTER_VALUE = "*";

    private final String level;
    private final String display;
    private final List<Map<String, String>> scopes;
    private final boolean administered;

    private SolrResource(String level, String display, List<Map<String, String>> scopes, boolean administered)
    {
        this.level = level;
        this.display = display;
        this.scopes = Collections.unmodifiableList(scopes);
        this.administered = administered;
    }

    /**
     * A collection, or a request path within it.
     *
     * @param collection the collection
     * @param path the request path, such as {@code /select}, or {@code null}
     * @return the resource
     */
    static SolrResource collection(String collection, @Nullable String path)
    {
        List<Map<String, String>> scopes = new ArrayList<>();
        Map<String, String> scope = new LinkedHashMap<>();
        scope.put(COLLECTION, collection);
        scopes.add(Collections.unmodifiableMap(new LinkedHashMap<>(scope)));
        if (path == null) {
            return new SolrResource(COLLECTION, "collection " + collection, scopes, true);
        }
        scope.put(PATH, path);
        scopes.add(Collections.unmodifiableMap(scope));
        return new SolrResource(PATH, "collection " + collection + " path " + path, scopes, true);
    }

    /**
     * The cluster, for its administration.
     *
     * @return the resource
     */
    static SolrResource cluster()
    {
        return new SolrResource(CLUSTER, CLUSTER, List.of(Collections.singletonMap(CLUSTER, CLUSTER_VALUE)), false);
    }

    /**
     * Returns the level the resource is of.
     *
     * @return collection, path or cluster
     */
    String level()
    {
        return level;
    }

    /**
     * Returns how audit events and refusals name the resource.
     *
     * @return such as {@code collection sales path /select}
     */
    String display()
    {
        return display;
    }

    /**
     * Returns the scopes the resource is decided at.
     *
     * @return the scopes, each the levels and values
     */
    List<Map<String, String>> scopes()
    {
        return scopes;
    }

    /**
     * Returns whether the cluster's administrators may do everything to the resource, as they may to all but the cluster
     * itself, which they administer anyway.
     *
     * @return {@code true} for a collection or a path
     */
    boolean administered()
    {
        return administered;
    }

    @Override
    public String toString()
    {
        return display;
    }
}
