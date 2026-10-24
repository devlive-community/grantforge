// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.naming.TopicName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PulsarResourceTest
{
    @Test
    void decidesATopicAtTheClusterAndEveryLevelOfItsName()
    {
        PulsarResource topic = PulsarResource.topic("west", TopicName.get("non-persistent://sales/orders/clicks"), null);
        assertThat(topic.level()).isEqualTo("topic");
        assertThat(topic.display()).isEqualTo("non-persistent://sales/orders/clicks").isEqualTo(topic.toString());
        assertThat(topic.cluster()).isEqualTo("west");
        assertThat(topic.scopes()).containsExactly(Map.of("tenant", "sales"), Map.of("tenant", "sales", "namespace", "orders"),
                Map.of("tenant", "sales", "namespace", "orders", "topic", "clicks"));
        assertThat(List.copyOf(topic.scopes().get(2).keySet())).containsExactly("tenant", "namespace", "topic");
    }

    @Test
    void decidesAPartitionAsItsTopicAndASubscriptionBelowIt()
    {
        PulsarResource subscription = PulsarResource.topic("west", TopicName.get("persistent://sales/orders/payments-partition-7"), "billing");
        assertThat(subscription.level()).isEqualTo("subscription");
        assertThat(subscription.display()).isEqualTo("persistent://sales/orders/payments subscription billing");
        assertThat(subscription.scopes()).hasSize(4).last()
                .isEqualTo(Map.of("tenant", "sales", "namespace", "orders", "topic", "payments", "subscription", "billing"));
    }

    @Test
    void decidesNamespacesTenantsAndClusters()
    {
        PulsarResource namespace = PulsarResource.namespace("west", NamespaceName.get("hr/payroll"));
        assertThat(namespace.display()).isEqualTo("namespace hr/payroll");
        assertThat(namespace.scopes()).containsExactly(Map.of("tenant", "hr"), Map.of("tenant", "hr", "namespace", "payroll"));
        assertThat(PulsarResource.tenant("west", "hr").scopes()).containsExactly(Map.of("tenant", "hr"));
        assertThat(PulsarResource.tenant("west", "hr").display()).isEqualTo("tenant hr");
        PulsarResource cluster = PulsarResource.cluster("east");
        assertThat(cluster.level()).isEqualTo("cluster");
        assertThat(cluster.display()).isEqualTo("cluster east");
        assertThat(cluster.scopes()).containsExactly(Map.of("cluster", "east"));
        assertThat(cluster.cluster()).isNull();
    }
}
