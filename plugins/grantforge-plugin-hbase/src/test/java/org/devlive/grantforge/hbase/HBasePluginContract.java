// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
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

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The HBase plugin against a real cluster of one HBase release: the connection test, lookups of namespaces, tables and
 * column families narrowed by what a policy names above them, bootstrap servers instead of ZooKeeper, and a cluster that
 * cannot be reached.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class HBasePluginContract
{
    private final HBaseProvider provider = new HBaseProvider();
    private @Nullable HBaseCluster cluster;

    /**
     * The release under test.
     *
     * @return such as {@code 2.6.7}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        HBaseCluster started = new HBaseCluster(version());
        cluster = started;
        started.start();
        Configuration configuration = new Configuration();
        configuration.set("hbase.zookeeper.quorum", started.quorum());
        configuration.setInt("hbase.zookeeper.property.clientPort", started.zookeeperPort());
        try (Connection connection = ConnectionFactory.createConnection(configuration); Admin admin = connection.getAdmin()) {
            admin.createNamespace(NamespaceDescriptor.create("sales").build());
            admin.createNamespace(NamespaceDescriptor.create("hr").build());
            create(admin, "sales:orders", "d", "meta");
            create(admin, "sales:items", "d");
            create(admin, "hr:salaries", "p");
            create(admin, "events", "e");
        }
    }

    private static void create(Admin admin, String table, String... families) throws Exception
    {
        TableDescriptorBuilder descriptor = TableDescriptorBuilder.newBuilder(TableName.valueOf(table));
        for (String family : families) {
            descriptor.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
        }
        admin.createTable(descriptor.build());
    }

    @AfterAll
    void stop()
    {
        if (cluster != null) {
            cluster.stop();
        }
    }

    private ServiceConfig service(@Nullable String... values)
    {
        HBaseCluster running = requireNonNull(cluster, "cluster");
        Map<String, String> map = new HashMap<>(Map.of(HBaseSettings.QUORUM, running.quorum(), HBaseSettings.ZOOKEEPER_PORT,
                Integer.toString(running.zookeeperPort()), HBaseSettings.USER, "grantforge", HBaseSettings.TIMEOUT, "10000"));
        for (int index = 0; index < values.length; index += 2) {
            String field = requireNonNull(values[index], "field");
            String value = values[index + 1];
            if (value == null) {
                map.remove(field);
            }
            else {
                map.put(field, value);
            }
        }
        return new ServiceConfig("tables", map);
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 50));
    }

    @Test
    void connectsAndDescribesTheCluster()
    {
        ConnectionResult result = provider.testConnection(service());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).startsWith("cluster ").contains(", HBase " + version()).endsWith("1 region server");
    }

    @Test
    void looksUpNamespacesTablesAndColumnFamilies()
    {
        ServiceConfig config = service();
        assertThat(lookup(config, "namespace", "", Map.of())).containsExactly("default", "hbase", "hr", "sales");
        assertThat(lookup(config, "namespace", "s", Map.of())).containsExactly("sales");
        assertThat(lookup(config, "table", "", Map.of("namespace", List.of("sales")))).containsExactly("items", "orders");
        assertThat(lookup(config, "table", "", Map.of("namespace", List.of("*")))).contains("events", "items", "orders", "salaries", "meta");
        assertThat(lookup(config, "table", "s", Map.of("namespace", List.of("h?")))).containsExactly("salaries");
        assertThat(lookup(config, "column_family", "", Map.of("namespace", List.of("sales"), "table", List.of("orders")))).containsExactly("d", "meta");
        assertThat(lookup(config, "column_family", "", Map.of("namespace", List.of("sales"), "table", List.of("*")))).containsExactly("d", "meta");
        assertThat(lookup(config, "column_family", "", Map.of("namespace", List.of("*"), "table", List.of("sal*")))).containsExactly("p");
        assertThat(lookup(config, "global", "", Map.of())).containsExactly("*");
    }

    @Test
    void findsTheClusterThroughBootstrapServers()
    {
        ServiceConfig config = service(HBaseSettings.QUORUM, null, HBaseSettings.ZOOKEEPER_PORT, null, HBaseSettings.BOOTSTRAP,
                requireNonNull(cluster, "cluster").regionServer());
        assertThat(provider.testConnection(config).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(lookup(config, "table", "o", Map.of("namespace", List.of("sales")))).containsExactly("orders");
    }

    @Test
    void namesAClusterThatCannotBeReached() throws Exception
    {
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        ServiceConfig config = service(HBaseSettings.QUORUM, null, HBaseSettings.ZOOKEEPER_PORT, null, HBaseSettings.BOOTSTRAP,
                "localhost:" + closed, HBaseSettings.TIMEOUT, "3000");
        assertThatThrownBy(() -> lookup(config, "namespace", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(provider.testConnection(config).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }
}
