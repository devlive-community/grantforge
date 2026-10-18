// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.security.AccessDeniedException;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.security.UserGroupInformation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.Testcontainers;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shaded agent inside a real HBase cluster of one release, taking signed policies from a GrantForge server: reads of
 * a whole namespace, of one column family and of a table but one column, which return only what may be read; writes to
 * columns; a namespace's owner creating it and its tables; listings that leave out what a user may not use; refusals that
 * say what was missing; repeated accesses folded into one audit event; and a policy change applied without a restart.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 12, unit = TimeUnit.MINUTES)
abstract class HBaseAgentContract
{
    private static final TableName ORDERS = TableName.valueOf("sales:orders");
    private static final TableName ITEMS = TableName.valueOf("sales:items");

    private @Nullable AgentServer server;
    private @Nullable HBaseCluster cluster;

    /**
     * The release under test.
     *
     * @return such as {@code 2.6.7}
     */
    abstract String version();

    private static HBaseSnapshots policies()
    {
        return new HBaseSnapshots()
                .allow(List.of("alice"), List.of("read"), "namespace", "sales")
                .allow(List.of("bob"), List.of("read"), "namespace", "sales", "table", "orders", "column_family", "d")
                .allow(List.of("@auditors"), List.of("read"), "namespace", "sales", "table", "orders")
                .deny(List.of("@auditors"), List.of("read"), "namespace", "sales", "table", "orders", "column_family", "d", "column", "ssn")
                .allow(List.of("dave"), List.of("create", "admin"), "namespace", "analytics")
                .allow(List.of("erin"), List.of("write"), "namespace", "sales", "table", "orders", "column_family", "d", "column", "*")
                .group("auditors", "carol");
    }

    @BeforeAll
    void start() throws Exception
    {
        Path jar = Path.of(System.getProperty("grantforge.hbase.agent.jar"));
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertThat(zip.getEntry("org/devlive/grantforge/hbase/agent/GrantForgeHBaseCoprocessor.class")).isNotNull();
            assertThat(zip.getEntry("org/devlive/grantforge/hbase/agent/internal/jackson/databind/ObjectMapper.class")).isNotNull();
            assertThat(zip.getEntry("org/devlive/grantforge/hbase/agent/internal/caffeine/cache/Caffeine.class")).isNotNull();
            assertThat(zip.getEntry("org/apache/hadoop/hbase/coprocessor/MasterObserver.class")).isNull();
            assertThat(zip.getEntry("com/fasterxml/jackson/databind/ObjectMapper.class")).isNull();
        }
        AgentServer agents = new AgentServer(policies());
        server = agents;
        Testcontainers.exposeHostPorts(agents.port());
        String coprocessor = GrantForgeHBaseCoprocessor.class.getName();
        HBaseCluster started = new HBaseCluster(version())
                .set("hbase.coprocessor.master.classes", coprocessor)
                .set("hbase.coprocessor.region.classes", coprocessor)
                .set("hbase.coprocessor.regionserver.classes", coprocessor)
                .set("grantforge.server.url", "http://host.testcontainers.internal:" + agents.port())
                .set("grantforge.token.file", "/opt/grantforge/token")
                .set("grantforge.signing.key.file", "/opt/grantforge/signing.key")
                .set("grantforge.instance", "hbase-it")
                .set("grantforge.cache.dir", "/tmp/grantforge-cache")
                .set("grantforge.refresh.interval.ms", "1000")
                .set("grantforge.audit.batch.size", "1")
                .set("grantforge.audit.flush.interval.ms", "200")
                .set("grantforge.audit.fold.window.ms", "2000");
        started.container().withCopyFileToContainer(MountableFile.forHostPath(jar), "/opt/hbase/lib/grantforge-agent-hbase.jar")
                .withCopyToContainer(Transferable.of(AgentServer.TOKEN.getBytes(StandardCharsets.UTF_8), 0644), "/opt/grantforge/token")
                .withCopyToContainer(Transferable.of(agents.publicKey().getBytes(StandardCharsets.US_ASCII), 0644), "/opt/grantforge/signing.key");
        cluster = started;
        started.start();
        try (Connection root = connect("root"); Admin admin = root.getAdmin()) {
            admin.createNamespace(NamespaceDescriptor.create("sales").build());
            admin.createTable(TableDescriptorBuilder.newBuilder(ORDERS).setColumnFamily(ColumnFamilyDescriptorBuilder.of("d"))
                    .setColumnFamily(ColumnFamilyDescriptorBuilder.of("meta")).build());
            admin.createTable(TableDescriptorBuilder.newBuilder(ITEMS).setColumnFamily(ColumnFamilyDescriptorBuilder.of("d")).build());
            try (Table orders = root.getTable(ORDERS); Table items = root.getTable(ITEMS)) {
                orders.put(new Put(Bytes.toBytes("o1")).addColumn(Bytes.toBytes("d"), Bytes.toBytes("name"), Bytes.toBytes("Ann"))
                        .addColumn(Bytes.toBytes("d"), Bytes.toBytes("ssn"), Bytes.toBytes("123")).addColumn(Bytes.toBytes("meta"),
                                Bytes.toBytes("source"), Bytes.toBytes("web")));
                items.put(new Put(Bytes.toBytes("i1")).addColumn(Bytes.toBytes("d"), Bytes.toBytes("sku"), Bytes.toBytes("A-1")));
            }
        }
    }

    @AfterAll
    void stop()
    {
        if (cluster != null) {
            cluster.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    private Connection connect(String user) throws Exception
    {
        HBaseCluster running = requireNonNull(cluster, "cluster");
        Configuration configuration = new Configuration();
        configuration.set("hbase.zookeeper.quorum", running.quorum());
        configuration.setInt("hbase.zookeeper.property.clientPort", running.zookeeperPort());
        configuration.setInt("hbase.client.retries.number", 2);
        return ConnectionFactory.createConnection(configuration, User.create(UserGroupInformation.createRemoteUser(user)));
    }

    /** The columns a user reads of a table, as family:qualifier. */
    private List<String> scan(String user, TableName table) throws Exception
    {
        TreeSet<String> columns = new TreeSet<>();
        try (Connection connection = connect(user); Table opened = connection.getTable(table); ResultScanner results = opened.getScanner(new Scan())) {
            for (Result result : results) {
                for (Cell cell : result.rawCells()) {
                    columns.add(Bytes.toString(CellUtil.cloneFamily(cell)) + ":" + Bytes.toString(CellUtil.cloneQualifier(cell)));
                }
            }
        }
        return new ArrayList<>(columns);
    }

    private List<String> get(String user, TableName table, String row) throws Exception
    {
        TreeSet<String> columns = new TreeSet<>();
        try (Connection connection = connect(user); Table opened = connection.getTable(table)) {
            for (Cell cell : opened.get(new Get(Bytes.toBytes(row))).rawCells()) {
                columns.add(Bytes.toString(CellUtil.cloneFamily(cell)) + ":" + Bytes.toString(CellUtil.cloneQualifier(cell)));
            }
        }
        return new ArrayList<>(columns);
    }

    /** The refusal a call met, wherever in the cause chain HBase's client put it. */
    private static AccessDeniedException refusal(Call call)
    {
        try {
            call.run();
        }
        catch (Exception failed) {
            for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
                if (cause instanceof AccessDeniedException denied) {
                    return denied;
                }
            }
            throw new AssertionError("not a refusal", failed);
        }
        throw new AssertionError("the call succeeded");
    }

    /** A call that may fail. */
    @FunctionalInterface
    private interface Call
    {
        void run() throws Exception;
    }

    @Test
    @Order(1)
    void readsAWholeNamespaceAColumnFamilyOrATableButOneColumn() throws Exception
    {
        assertThat(scan("alice", ORDERS)).containsExactly("d:name", "d:ssn", "meta:source");
        assertThat(scan("alice", ITEMS)).containsExactly("d:sku");
        assertThat(scan("bob", ORDERS)).as("only the column family bob may read").containsExactly("d:name", "d:ssn");
        assertThat(get("bob", ORDERS, "o1")).containsExactly("d:name", "d:ssn");
        assertThat(scan("carol", ORDERS)).as("the auditors' group, denied one column").containsExactly("d:name", "meta:source");
        assertThat(get("carol", ORDERS, "o1")).containsExactly("d:name", "meta:source");
        AccessDeniedException refused = refusal(() -> scan("bob", ITEMS));
        assertThat(refused).hasMessageContaining("user bob has no read on table sales:items").hasMessageContaining("no GrantForge policy allows it");
    }

    @Test
    @Order(2)
    void writesOnlyTheColumnsAPolicyNames() throws Exception
    {
        try (Connection connection = connect("erin"); Table orders = connection.getTable(ORDERS)) {
            orders.put(new Put(Bytes.toBytes("o2")).addColumn(Bytes.toBytes("d"), Bytes.toBytes("name"), Bytes.toBytes("Ben")));
            assertThat(refusal(() -> orders.put(new Put(Bytes.toBytes("o2")).addColumn(Bytes.toBytes("meta"), Bytes.toBytes("source"),
                    Bytes.toBytes("api"))))).hasMessageContaining("user erin has no write on column sales:orders/meta:source");
            assertThat(refusal(() -> orders.delete(new Delete(Bytes.toBytes("o2"))))).as("HBase deletes a row as each of its column families")
                    .hasMessageContaining("no write on column family sales:orders/meta");
        }
        assertThat(get("alice", ORDERS, "o2")).containsExactly("d:name");
        assertThat(refusal(() -> {
            try (Connection connection = connect("alice"); Table orders = connection.getTable(ORDERS)) {
                orders.put(new Put(Bytes.toBytes("o3")).addColumn(Bytes.toBytes("d"), Bytes.toBytes("name"), Bytes.toBytes("Cid")));
            }
        })).hasMessageContaining("user alice has no write");
    }

    @Test
    @Order(3)
    void letsANamespacesOwnerCreateItAndItsTables() throws Exception
    {
        try (Connection connection = connect("dave"); Admin admin = connection.getAdmin()) {
            admin.createNamespace(NamespaceDescriptor.create("analytics").build());
            TableName events = TableName.valueOf("analytics:events");
            admin.createTable(TableDescriptorBuilder.newBuilder(events).setColumnFamily(ColumnFamilyDescriptorBuilder.of("e")).build());
            admin.disableTable(events);
            admin.deleteTable(events);
            assertThat(refusal(() -> admin.createTable(TableDescriptorBuilder.newBuilder(TableName.valueOf("sales:stolen"))
                    .setColumnFamily(ColumnFamilyDescriptorBuilder.of("x")).build()))).hasMessageContaining("user dave has no create on namespace sales");
            assertThat(refusal(admin::listReplicationPeers)).hasMessageContaining("user dave has no admin on global *");
            // HBase logs a refusal of the balancer switch instead of returning it: the switch stays as it was.
            admin.balancerSwitch(false, true);
        }
        try (Connection root = connect("root"); Admin admin = root.getAdmin()) {
            assertThat(admin.isBalancerEnabled()).isTrue();
        }
    }

    @Test
    @Order(4)
    void listsOnlyWhatAUserMayUse() throws Exception
    {
        try (Connection connection = connect("bob"); Admin admin = connection.getAdmin()) {
            assertThat(admin.listTableNames()).containsExactly(ORDERS);
            List<String> namespaces = new ArrayList<>();
            for (NamespaceDescriptor namespace : admin.listNamespaceDescriptors()) {
                namespaces.add(namespace.getName());
            }
            assertThat(namespaces).containsExactlyInAnyOrder("hbase", "sales");
            List<TableName> described = new ArrayList<>();
            for (TableDescriptor descriptor : admin.listTableDescriptors()) {
                described.add(descriptor.getTableName());
            }
            assertThat(described).containsExactly(ORDERS);
        }
        try (Connection connection = connect("alice"); Admin admin = connection.getAdmin()) {
            assertThat(admin.listTableNames()).containsExactlyInAnyOrder(ORDERS, ITEMS);
        }
    }

    private List<JsonNode> waitForEvents(Predicate<JsonNode> wanted) throws InterruptedException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            List<JsonNode> found = requireNonNull(server, "server").events().stream().filter(wanted).toList();
            if (!found.isEmpty() || System.nanoTime() > deadline) {
                return found;
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }

    private static Predicate<JsonNode> event(String user, String resource, String accessType, String outcome)
    {
        return node -> user.equals(node.path("user").asText()) && resource.equals(node.path("resource").asText())
                && accessType.equals(node.path("accessType").asText()) && outcome.equals(node.path("outcome").asText());
    }

    @Test
    @Order(5)
    void foldsRepeatedReadsIntoOneEventAndReportsRefusals() throws Exception
    {
        for (int round = 0; round < 10; round++) {
            get("alice", ITEMS, "i1");
        }
        List<JsonNode> reads = waitForEvents(event("alice", "sales:items", "read", "ALLOWED").and(node -> node.path("count").asInt(1) > 1));
        assertThat(reads).as("of %s", requireNonNull(server, "server").events()).isNotEmpty();
        assertThat(reads.get(0).path("action").asText()).isEqualTo("get");
        assertThat(waitForEvents(event("bob", "sales:items", "read", "DENIED"))).isNotEmpty();
        assertThat(waitForEvents(event("bob", "sales:orders", "read", "ALLOWED").and(node -> node.path("request").asText().contains(
                "only the columns")))).as("a read of one column family").isNotEmpty();
        assertThat(requireNonNull(server, "server").events()).as("superusers are not audited").noneMatch(node -> "root".equals(node.path("user")
                .asText()));
    }

    @Test
    @Order(6)
    void appliesAPolicyChangeWithoutARestart() throws Exception
    {
        requireNonNull(server, "server").publish(policies().allow(List.of("bob"), List.of("read"), "namespace", "sales", "table", "items"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            try {
                assertThat(scan("bob", ITEMS)).containsExactly("d:sku");
                break;
            }
            catch (Exception refused) {
                if (System.nanoTime() > deadline) {
                    throw refused;
                }
                TimeUnit.MILLISECONDS.sleep(500);
            }
        }
    }
}
