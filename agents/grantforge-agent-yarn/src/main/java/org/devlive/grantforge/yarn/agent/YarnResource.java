// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What YARN asks about, as the levels of the YARN service type name it: a queue by its path, an application by its name
 * in a queue, or the cluster. As in YARN, a permission on a queue covers the queues below it, so a queue is decided at
 * every queue of its path, from the root; an application is decided at each of them too, by the queue alone and by the
 * queue with its name, so a policy can allow some applications in a parent queue and its children.
 */
final class YarnResource
{
    static final String QUEUE = "queue";
    static final String APPLICATION = "application";
    static final String CLUSTER = "cluster";

    /** The one value of the cluster level. */
    static final String CLUSTER_VALUE = "*";

    private final String level;
    private final String display;
    private final List<Map<String, String>> scopes;

    private YarnResource(String level, String display, List<Map<String, String>> scopes)
    {
        this.level = level;
        this.display = display;
        this.scopes = Collections.unmodifiableList(scopes);
    }

    /**
     * A queue, or an application in it.
     *
     * @param queue the queue's path, such as {@code root.sales.etl}
     * @param application the application's name, or {@code null}
     * @return the resource
     */
    static YarnResource queue(String queue, @Nullable String application)
    {
        List<Map<String, String>> scopes = new ArrayList<>();
        int end = queue.indexOf('.');
        while (true) {
            String path = end < 0 ? queue : queue.substring(0, end);
            scopes.add(scope(path, null));
            if (application != null) {
                scopes.add(scope(path, application));
            }
            if (end < 0) {
                break;
            }
            end = queue.indexOf('.', end + 1);
        }
        return application == null ? new YarnResource(QUEUE, queue, scopes)
                : new YarnResource(APPLICATION, queue + " application " + application, scopes);
    }

    private static Map<String, String> scope(String queue, @Nullable String application)
    {
        Map<String, String> scope = new LinkedHashMap<>();
        scope.put(QUEUE, queue);
        if (application != null) {
            scope.put(APPLICATION, application);
        }
        return Collections.unmodifiableMap(scope);
    }

    /**
     * The cluster, for YARN's administration.
     *
     * @return the resource
     */
    static YarnResource cluster()
    {
        Map<String, String> scope = new LinkedHashMap<>();
        scope.put(CLUSTER, CLUSTER_VALUE);
        return new YarnResource(CLUSTER, CLUSTER, Collections.singletonList(Collections.unmodifiableMap(scope)));
    }

    /**
     * Returns the level the resource is of.
     *
     * @return queue, application or cluster
     */
    String level()
    {
        return level;
    }

    /**
     * Returns how audit events and refusals name the resource.
     *
     * @return such as {@code root.sales.etl application etl-daily}
     */
    String display()
    {
        return display;
    }

    /**
     * Returns the scopes the resource is decided at, from the root.
     *
     * @return the scopes, each the levels and values
     */
    List<Map<String, String>> scopes()
    {
        return scopes;
    }

    @Override
    public String toString()
    {
        return display;
    }
}
