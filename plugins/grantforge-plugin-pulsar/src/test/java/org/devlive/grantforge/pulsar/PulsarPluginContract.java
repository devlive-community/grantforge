// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.AuthenticationFactory;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Pulsar plugin against a real broker of one Pulsar release: the connection test, lookups of every level, and sign-ins
 * and requests the broker refuses, and a broker that cannot be reached.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class PulsarPluginContract
{
    private final PulsarProvider provider = new PulsarProvider();
    private @Nullable PulsarCluster cluster;

    /**
     * The release under test.
     *
     * @return such as {@code 4.2.5}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        PulsarCluster started = new PulsarCluster(version());
        cluster = started;
        started.start();
        try (PulsarAdmin admin = PulsarAdmin.builder().serviceHttpUrl(started.webServiceUrl())
                .authentication(AuthenticationFactory.token(started.token(PulsarCluster.SUPERUSER))).build()) {
            Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
            while (!admin.tenants().getTenants().contains("public") && Instant.now().isBefore(deadline)) {
                TimeUnit.MILLISECONDS.sleep(500);
            }
            List<String> clusters = admin.clusters().getClusters();
            admin.tenants().createTenant("sales", TenantInfo.builder().allowedClusters(Set.copyOf(clusters)).build());
            admin.namespaces().createNamespace("sales/orders");
            admin.namespaces().createNamespace("sales/returns");
            admin.topics().createPartitionedTopic("persistent://sales/orders/payments", 3);
            admin.topics().createNonPartitionedTopic("persistent://sales/returns/refunds");
            admin.topics().createSubscription("persistent://sales/orders/payments", "billing", MessageId.earliest);
            admin.topics().createSubscription("persistent://sales/orders/payments", "audit", MessageId.earliest);
        }
    }

    @AfterAll
    void stop()
    {
        if (cluster != null) {
            cluster.close();
        }
    }

    private PulsarCluster cluster()
    {
        return requireNonNull(cluster, "cluster");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(PulsarSettings.WEB_SERVICE_URL, cluster().webServiceUrl(),
                PulsarSettings.AUTHENTICATION, PulsarSettings.TOKEN, PulsarSettings.TOKEN_FIELD, cluster().token(PulsarCluster.SUPERUSER),
                PulsarSettings.TIMEOUT, "10000"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("events", values);
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 20));
    }

    @Test
    void testsTheConnection()
    {
        ConnectionResult result = provider.testConnection(config());
        assertThat(result.status()).as(result.message()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).startsWith("cluster standalone, ").contains("tenants");
    }

    @Test
    void looksUpEveryLevel()
    {
        assertThat(lookup(config(), "cluster", "", Map.of())).containsExactly("standalone");
        assertThat(lookup(config(), "tenant", "", Map.of())).contains("public", "sales");
        assertThat(lookup(config(), "namespace", "", Map.of("tenant", List.of("sales")))).containsExactly("orders", "returns");
        assertThat(lookup(config(), "topic", "", Map.of("tenant", List.of("sales"), "namespace", List.of("*")))).containsExactly("payments", "refunds");
        assertThat(lookup(config(), "subscription", "", Map.of("tenant", List.of("sales"), "namespace", List.of("orders"), "topic", List.of("pay*"))))
                .containsExactly("audit", "billing");
    }

    @Test
    void refusesATokenTheBrokerDidNotSign()
    {
        ServiceConfig forged = config(PulsarSettings.TOKEN_FIELD, PulsarCluster.token(PulsarCluster.SUPERUSER, new byte[32]));
        assertThatThrownBy(() -> lookup(forged, "tenant", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        assertThat(provider.testConnection(forged).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void namesWhatTheLookupRoleMayNotSee()
    {
        ServiceConfig reader = config(PulsarSettings.TOKEN_FIELD, cluster().token("reader"));
        assertThatThrownBy(() -> lookup(reader, "tenant", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
    }

    @Test
    void namesABrokerThatCannotBeReached()
    {
        ServiceConfig nowhere = config(PulsarSettings.WEB_SERVICE_URL, "http://127.0.0.1:1", PulsarSettings.TIMEOUT, "3000");
        assertThatThrownBy(() -> lookup(nowhere, "tenant", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }
}
