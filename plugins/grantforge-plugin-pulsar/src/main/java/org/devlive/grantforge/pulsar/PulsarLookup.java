// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Lists the tenants, namespaces, topics, subscriptions or clusters that start with what was typed. Below the tenant, the
 * values the policy names for the levels above narrow where to look: the namespaces of its tenants, the topics of its
 * namespaces and the subscriptions of its topics. A topic is named without its domain and, when partitioned, by the
 * partitioned topic's name, as the agent decides it. Places the lookup role may not see are left out.
 *
 * @param resource the level
 * @param typed what was typed
 * @param context the values the policy names for the levels above, by level
 * @param limit the most names returned
 */
record PulsarLookup(String resource, String typed, Map<String, List<String>> context, int limit)
{
    /** The most tenants, namespaces or topics one lookup asks Pulsar about. */
    static final int MAX_ASKED = 50;

    private static final Pattern PARTITION = Pattern.compile("-partition-\\d+$");
    private static final List<String> LEVELS = List.of(PulsarProvider.TENANT, PulsarProvider.NAMESPACE, PulsarProvider.TOPIC, PulsarProvider.SUBSCRIPTION,
            PulsarProvider.CLUSTER);

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
    static PulsarLookup of(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        if (!LEVELS.contains(resource)) {
            throw new IllegalArgumentException("Pulsar services look up tenants, namespaces, topics, subscriptions and clusters, not " + resource);
        }
        return new PulsarLookup(resource, typed.strip(), context, limit);
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
     * Returns the name a topic is decided by: its own name without the domain, tenant and namespace, and without the
     * partition of a partitioned topic.
     *
     * @param topic such as {@code persistent://sales/orders/payments-partition-2}
     * @return such as {@code payments}
     */
    static String topicName(String topic)
    {
        String local = topic.substring(topic.lastIndexOf('/') + 1);
        return PARTITION.matcher(local).replaceFirst("");
    }

    /**
     * Runs the lookup.
     *
     * @param admin the open admin client
     * @return the names, sorted, at most {@link #limit()}
     * @throws PulsarAdminException when Pulsar fails the first request
     */
    List<String> run(PulsarAdmin admin) throws PulsarAdminException
    {
        SortedSet<String> names = new TreeSet<>();
        if (PulsarProvider.CLUSTER.equals(resource)) {
            names.addAll(admin.clusters().getClusters());
            return first(names);
        }
        if (PulsarProvider.TENANT.equals(resource)) {
            names.addAll(admin.tenants().getTenants());
            return first(names);
        }
        List<String> namespaces = namespaces(admin);
        if (PulsarProvider.NAMESPACE.equals(resource)) {
            for (String namespace : namespaces) {
                names.add(namespace.substring(namespace.indexOf('/') + 1));
            }
            return first(names);
        }
        List<String> topics = topics(admin, namespaces);
        if (PulsarProvider.TOPIC.equals(resource)) {
            for (String topic : topics) {
                names.add(topicName(topic));
            }
            return first(names);
        }
        for (String topic : topics.subList(0, Math.min(topics.size(), MAX_ASKED))) {
            names.addAll(visible(() -> admin.topics().getSubscriptions(topic)));
        }
        return first(names);
    }

    /** The namespaces, {@code tenant/namespace}, of the tenants the policy names. */
    private List<String> namespaces(PulsarAdmin admin) throws PulsarAdminException
    {
        List<Pattern> tenants = patterns(PulsarProvider.TENANT);
        List<Pattern> namespaces = patterns(PulsarProvider.NAMESPACE);
        List<String> asked = new ArrayList<>();
        for (String tenant : admin.tenants().getTenants()) {
            if (asked.size() < MAX_ASKED && matches(tenants, tenant)) {
                asked.add(tenant);
            }
        }
        List<String> found = new ArrayList<>();
        for (String tenant : asked) {
            for (String namespace : visible(() -> admin.namespaces().getNamespaces(tenant))) {
                String local = namespace.substring(namespace.indexOf('/') + 1);
                if (PulsarProvider.NAMESPACE.equals(resource) || matches(namespaces, local)) {
                    found.add(namespace);
                }
            }
        }
        return found;
    }

    /** The topics, by their full names, of the namespaces the policy names: partitioned ones once, by their own name. */
    private List<String> topics(PulsarAdmin admin, List<String> namespaces)
    {
        List<Pattern> topics = patterns(PulsarProvider.TOPIC);
        Set<String> found = new LinkedHashSet<>();
        for (String namespace : namespaces.subList(0, Math.min(namespaces.size(), MAX_ASKED))) {
            Set<String> all = new LinkedHashSet<>(visible(() -> admin.topics().getPartitionedTopicList(namespace)));
            for (String topic : visible(() -> admin.topics().getList(namespace))) {
                all.add(PARTITION.matcher(topic).replaceFirst(""));
            }
            for (String topic : all) {
                if (PulsarProvider.TOPIC.equals(resource) || matches(topics, topicName(topic))) {
                    found.add(topic);
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** A request whose place the lookup role may not see, or that is gone, finds nothing there. */
    private static List<String> visible(Request request)
    {
        try {
            return request.list();
        }
        catch (PulsarAdminException refused) {
            int status = refused.getStatusCode();
            if (status == 401 || status == 403 || status == 404) {
                return List.of();
            }
            throw new IllegalStateException(PulsarAdminClient.message(refused), refused);
        }
    }

    /** One listing request. */
    @FunctionalInterface
    private interface Request
    {
        List<String> list() throws PulsarAdminException;
    }

    private List<Pattern> patterns(String level)
    {
        List<String> values = context.get(level);
        List<Pattern> patterns = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return patterns;
        }
        for (String value : values) {
            String wildcard = value.strip();
            if ("*".equals(wildcard)) {
                return new ArrayList<>();
            }
            patterns.add(pattern(wildcard));
        }
        return patterns;
    }

    /** No patterns match everything. */
    private static boolean matches(List<Pattern> patterns, String name)
    {
        return patterns.isEmpty() || patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches());
    }

    private List<String> first(SortedSet<String> names)
    {
        List<String> first = new ArrayList<>();
        for (String name : names) {
            if (first.size() < limit && name.startsWith(typed)) {
                first.add(name);
            }
        }
        return first;
    }
}
