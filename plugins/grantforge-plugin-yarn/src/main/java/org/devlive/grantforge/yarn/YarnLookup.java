// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Lists the queues, or the names of the applications in the queues a policy names, that start with what was typed.
 * Queues are named by their path from the root, such as {@code root.sales.etl}, whichever scheduler the cluster runs; a
 * queue also matches by its own name, so typing {@code etl} finds it. Application names are those the ResourceManager
 * still knows: running applications and the most recent finished ones.
 *
 * @param resource the level, queue or application
 * @param typed what was typed
 * @param queues the queue values the policy names, or {@code *} for every queue
 * @param limit the most names returned
 */
record YarnLookup(String resource, String typed, List<String> queues, int limit)
{
    /** The most values of the queue level a lookup of applications is narrowed by; a policy naming more is narrowed by none. */
    static final int MAX_QUEUES = 50;

    /** The queue every other queue is below. */
    static final String ROOT = "root";

    private static final List<String> ANY = List.of("*");

    /**
     * Builds the lookup of one level.
     *
     * @param resource queue or application
     * @param typed what the user typed
     * @param context the values the policy names for the levels above
     * @param limit the most names returned
     * @return the lookup
     * @throws IllegalArgumentException for a level without lookups
     */
    static YarnLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!YarnProvider.QUEUE.equals(resource) && !YarnProvider.APPLICATION.equals(resource)) {
            throw new IllegalArgumentException("YARN services look up queues and applications, not " + resource);
        }
        return new YarnLookup(resource, typed.strip(), values(context.get(YarnProvider.QUEUE)), limit);
    }

    /** A policy's queue values; none, any or too many look everywhere. */
    private static List<String> values(@Nullable List<String> values)
    {
        if (values == null || values.isEmpty() || values.size() > MAX_QUEUES || values.stream().anyMatch(value -> "*".equals(value.strip()))) {
            return ANY;
        }
        return values.stream().map(String::strip).distinct().toList();
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
     * @param client the started client
     * @return the names, sorted, at most {@link #limit()}
     * @throws IOException when the ResourceManager cannot be asked
     * @throws YarnException when YARN fails the request
     */
    List<String> run(YarnClient client) throws IOException, YarnException
    {
        Map<String, String> paths = paths(client.getRootQueueInfos());
        SortedSet<String> names = new TreeSet<>();
        if (YarnProvider.QUEUE.equals(resource)) {
            for (Map.Entry<String, String> queue : paths.entrySet()) {
                String path = queue.getKey();
                if (path.startsWith(typed) || queue.getValue().startsWith(typed)) {
                    names.add(path);
                }
            }
        }
        else {
            List<Pattern> patterns = queues.stream().map(YarnLookup::pattern).toList();
            for (ApplicationReport application : client.getApplications()) {
                String name = application.getName();
                String path = path(paths, application.getQueue());
                if (name != null && name.startsWith(typed) && patterns.stream().anyMatch(pattern -> pattern.matcher(path).matches())) {
                    names.add(name);
                }
            }
        }
        List<String> found = new ArrayList<>();
        for (String name : names) {
            if (found.size() < limit) {
                found.add(name);
            }
        }
        return found;
    }

    /**
     * Returns the path of every queue, each with the queue's own name: the root queue and those below it, as the client
     * gives them, the root's children.
     *
     * @param children the root queue's children, or the root queue itself
     * @return the paths, from the root
     */
    static Map<String, String> paths(List<QueueInfo> children)
    {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put(ROOT, ROOT);
        for (QueueInfo child : children) {
            collect(child, ROOT, paths);
        }
        return paths;
    }

    private static void collect(QueueInfo queue, String parent, Map<String, String> paths)
    {
        String name = queue.getQueueName();
        // The fair scheduler names a queue by its whole path, the capacity scheduler by its own name.
        String path = name.equals(parent) || name.startsWith(parent + ".") ? name : parent + "." + name;
        paths.put(path, path.substring(path.lastIndexOf('.') + 1));
        List<QueueInfo> children = queue.getChildQueues();
        if (children != null) {
            for (QueueInfo child : children) {
                collect(child, path, paths);
            }
        }
    }

    /** The path of a queue an application report names, by its own name or its path. */
    static String path(Map<String, String> paths, @Nullable String queue)
    {
        if (queue == null) {
            return "";
        }
        if (paths.containsKey(queue)) {
            return queue;
        }
        for (Map.Entry<String, String> known : paths.entrySet()) {
            if (known.getValue().equals(queue)) {
                return known.getKey();
            }
        }
        return queue;
    }
}
