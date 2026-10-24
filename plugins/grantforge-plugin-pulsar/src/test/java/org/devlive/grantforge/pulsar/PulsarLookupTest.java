// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import org.apache.pulsar.client.admin.Clusters;
import org.apache.pulsar.client.admin.Namespaces;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Tenants;
import org.apache.pulsar.client.admin.Topics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PulsarLookupTest
{
    private final PulsarAdmin admin = mock(PulsarAdmin.class);
    private final Tenants tenants = mock(Tenants.class);
    private final Namespaces namespaces = mock(Namespaces.class);
    private final Topics topics = mock(Topics.class);
    private final Clusters clusters = mock(Clusters.class);

    @BeforeEach
    void cluster() throws PulsarAdminException
    {
        when(admin.tenants()).thenReturn(tenants);
        when(admin.namespaces()).thenReturn(namespaces);
        when(admin.topics()).thenReturn(topics);
        when(admin.clusters()).thenReturn(clusters);
        when(clusters.getClusters()).thenReturn(List.of("west", "east"));
        when(tenants.getTenants()).thenReturn(List.of("sales", "hr", "public"));
        when(namespaces.getNamespaces("sales")).thenReturn(List.of("sales/orders", "sales/returns"));
        when(namespaces.getNamespaces("hr")).thenReturn(List.of("hr/payroll"));
        when(namespaces.getNamespaces("public")).thenThrow(new PulsarAdminException(null, "Forbidden", 403));
        when(topics.getPartitionedTopicList("sales/orders")).thenReturn(List.of("persistent://sales/orders/payments"));
        when(topics.getList("sales/orders")).thenReturn(List.of("persistent://sales/orders/payments-partition-0",
                "persistent://sales/orders/payments-partition-1", "non-persistent://sales/orders/clicks"));
        when(topics.getPartitionedTopicList("sales/returns")).thenReturn(List.of());
        when(topics.getList("sales/returns")).thenReturn(List.of("persistent://sales/returns/refunds"));
        when(topics.getPartitionedTopicList("hr/payroll")).thenThrow(new PulsarAdminException(null, "gone", 404));
        when(topics.getList("hr/payroll")).thenReturn(List.of("persistent://hr/payroll/salaries"));
        when(topics.getSubscriptions("persistent://sales/orders/payments")).thenReturn(List.of("billing", "audit"));
        when(topics.getSubscriptions("non-persistent://sales/orders/clicks")).thenReturn(List.of("analytics"));
        when(topics.getSubscriptions("persistent://sales/returns/refunds")).thenThrow(new PulsarAdminException(null, "Unauthorized", 401));
    }

    private List<String> lookup(String resource, String typed, Map<String, List<String>> context) throws PulsarAdminException
    {
        return PulsarLookup.of(resource, typed, context, 10).run(admin);
    }

    @Test
    void listsClustersAndTenants() throws PulsarAdminException
    {
        assertThat(lookup("cluster", "", Map.of())).containsExactly("east", "west");
        assertThat(lookup("tenant", "s", Map.of())).containsExactly("sales");
        assertThat(PulsarLookup.of("tenant", " ", Map.of(), 2).run(admin)).containsExactly("hr", "public");
    }

    @Test
    void listsTheNamespacesOfTheTenantsAPolicyNames() throws PulsarAdminException
    {
        assertThat(lookup("namespace", "", Map.of("tenant", List.of("sales")))).containsExactly("orders", "returns");
        assertThat(lookup("namespace", "", Map.of("tenant", List.of("*")))).containsExactly("orders", "payroll", "returns");
        assertThat(lookup("namespace", "r", Map.of())).containsExactly("returns");
        assertThat(lookup("namespace", "", Map.of("tenant", List.of("h?")))).containsExactly("payroll");
    }

    @Test
    void listsTopicsByTheirOwnNamesOnce() throws PulsarAdminException
    {
        assertThat(lookup("topic", "", Map.of("tenant", List.of("sales"), "namespace", List.of("orders")))).containsExactly("clicks", "payments");
        assertThat(lookup("topic", "", Map.of("tenant", List.of("*"), "namespace", List.of("*")))).containsExactly("clicks", "payments", "refunds",
                "salaries");
        assertThat(lookup("topic", "re", Map.of("tenant", List.of("sales"), "namespace", List.of("ret*")))).containsExactly("refunds");
    }

    @Test
    void listsTheSubscriptionsOfTheTopicsAPolicyNames() throws PulsarAdminException
    {
        assertThat(lookup("subscription", "", Map.of("tenant", List.of("sales"), "namespace", List.of("orders"), "topic", List.of("payments"))))
                .containsExactly("audit", "billing");
        assertThat(lookup("subscription", "", Map.of("tenant", List.of("sales")))).containsExactly("analytics", "audit", "billing");
        verify(topics, times(2)).getSubscriptions("persistent://sales/orders/payments");
    }

    @Test
    void asksAboutAtMostSoManyPlaces() throws PulsarAdminException
    {
        List<String> many = new ArrayList<>();
        for (int index = 0; index < PulsarLookup.MAX_ASKED + 5; index++) {
            many.add("t" + index);
        }
        when(tenants.getTenants()).thenReturn(many);
        when(namespaces.getNamespaces(anyString())).thenReturn(List.of());
        assertThat(lookup("namespace", "", Map.of())).isEmpty();
        verify(namespaces, times(PulsarLookup.MAX_ASKED)).getNamespaces(anyString());
    }

    @Test
    void failsWhenPulsarFailsOtherwise() throws PulsarAdminException
    {
        when(namespaces.getNamespaces("hr")).thenThrow(new PulsarAdminException(null, "Internal error", 500));
        assertThatThrownBy(() -> lookup("namespace", "", Map.of())).isInstanceOf(IllegalStateException.class).hasMessage("Internal error");
        verify(topics, never()).getList(anyString());
    }

    @Test
    void namesTopicsAsTheAgentDecidesThem()
    {
        assertThat(PulsarLookup.topicName("persistent://sales/orders/payments-partition-12")).isEqualTo("payments");
        assertThat(PulsarLookup.topicName("non-persistent://sales/orders/clicks")).isEqualTo("clicks");
        assertThat(PulsarLookup.topicName("payments")).isEqualTo("payments");
    }

    @Test
    void turnsWildcardsIntoPatterns()
    {
        assertThat(PulsarLookup.pattern("pay*").matcher("payments").matches()).isTrue();
        assertThat(PulsarLookup.pattern("h?").matcher("hr").matches()).isTrue();
        assertThat(PulsarLookup.pattern("a.b").matcher("axb").matches()).isFalse();
    }

    @Test
    void refusesALevelPulsarDoesNotHave()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> PulsarLookup.of("queue", "", Map.of(), 10)).withMessageContaining("queue");
    }
}
