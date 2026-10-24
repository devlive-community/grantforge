// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.naming.TopicName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Pulsar asks about, as the levels of the Pulsar service type name it: a tenant, a namespace in it, a topic in that
 * and a subscription of the topic, or a cluster. As with Pulsar's own grants, a permission on a tenant or a namespace
 * covers what is in it, so a resource is decided at its tenant, its namespace, its topic and its subscription in turn;
 * and as Pulsar's superusers may do everything, the administrators of the broker's cluster may too. A topic is decided by
 * its own name, without its domain, and a partition of a partitioned topic as the partitioned topic.
 *
 * @param level the level the resource is of
 * @param display how audit events and refusals name it
 * @param scopes the scopes it is decided at, from the tenant down, each the levels and values
 * @param cluster the cluster whose administrators may do everything to it, or {@code null} for a cluster itself
 */
record PulsarResource(String level, String display, List<Map<String, String>> scopes, @Nullable String cluster)
{
    static final String TENANT = "tenant";
    static final String NAMESPACE = "namespace";
    static final String TOPIC = "topic";
    static final String SUBSCRIPTION = "subscription";
    static final String CLUSTER = "cluster";

    PulsarResource
    {
        scopes = List.copyOf(scopes);
    }

    /**
     * A cluster, for its administration.
     *
     * @param cluster the cluster's name
     * @return the resource
     */
    static PulsarResource cluster(String cluster)
    {
        return new PulsarResource(CLUSTER, "cluster " + cluster, List.of(Collections.singletonMap(CLUSTER, cluster)), null);
    }

    /**
     * A tenant.
     *
     * @param cluster the broker's cluster
     * @param tenant the tenant
     * @return the resource
     */
    static PulsarResource tenant(String cluster, String tenant)
    {
        return new PulsarResource(TENANT, "tenant " + tenant, scopes(tenant, null, null, null), cluster);
    }

    /**
     * A namespace.
     *
     * @param cluster the broker's cluster
     * @param namespace the namespace
     * @return the resource
     */
    static PulsarResource namespace(String cluster, NamespaceName namespace)
    {
        return new PulsarResource(NAMESPACE, "namespace " + namespace, scopes(namespace.getTenant(), namespace.getLocalName(), null, null), cluster);
    }

    /**
     * A topic, or a subscription of it.
     *
     * @param cluster the broker's cluster
     * @param topic the topic, or a partition of it
     * @param subscription the subscription, or {@code null}
     * @return the resource
     */
    static PulsarResource topic(String cluster, TopicName topic, @Nullable String subscription)
    {
        String partitioned = topic.getPartitionedTopicName();
        TopicName base = TopicName.get(partitioned);
        List<Map<String, String>> scopes = scopes(base.getTenant(), base.getNamespacePortion(), base.getLocalName(), subscription);
        return subscription == null ? new PulsarResource(TOPIC, partitioned, scopes, cluster)
                : new PulsarResource(SUBSCRIPTION, partitioned + " subscription " + subscription, scopes, cluster);
    }

    private static List<Map<String, String>> scopes(String tenant, @Nullable String namespace, @Nullable String topic, @Nullable String subscription)
    {
        List<Map<String, String>> scopes = new ArrayList<>();
        Map<String, String> scope = new LinkedHashMap<>();
        scope.put(TENANT, tenant);
        scopes.add(Collections.unmodifiableMap(new LinkedHashMap<>(scope)));
        if (namespace != null) {
            scope.put(NAMESPACE, namespace);
            scopes.add(Collections.unmodifiableMap(new LinkedHashMap<>(scope)));
            if (topic != null) {
                scope.put(TOPIC, topic);
                scopes.add(Collections.unmodifiableMap(new LinkedHashMap<>(scope)));
                if (subscription != null) {
                    scope.put(SUBSCRIPTION, subscription);
                    scopes.add(Collections.unmodifiableMap(new LinkedHashMap<>(scope)));
                }
            }
        }
        return scopes;
    }

    @Override
    public String toString()
    {
        return display;
    }
}
