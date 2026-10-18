// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.CheckAndMutate;
import org.apache.hadoop.hbase.client.CheckAndMutateResult;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.SnapshotDescription;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.coprocessor.MasterCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.ObserverContextImpl;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.RegionServerCoprocessorEnvironment;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.KeyOnlyFilter;
import org.apache.hadoop.hbase.regionserver.FlushLifeCycleTracker;
import org.apache.hadoop.hbase.regionserver.MiniBatchOperationInProgress;
import org.apache.hadoop.hbase.regionserver.Store;
import org.apache.hadoop.hbase.regionserver.compactions.CompactionLifeCycleTracker;
import org.apache.hadoop.hbase.security.AccessDeniedException;
import org.apache.hadoop.hbase.security.Superusers;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.util.Bytes;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.GrantForgeAgent;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrantForgeHBaseCoprocessorTest
{
    private static final TableName ORDERS = TableName.valueOf("sales:orders");
    private static final TableName META = TableName.valueOf("hbase:meta");
    private static final byte[] D = Bytes.toBytes("d");

    @TempDir
    Path temporary;

    private final GrantForgeAgent agent = mock(GrantForgeAgent.class);
    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();
    private final List<AccessEvent> events = new CopyOnWriteArrayList<>();
    private final Function<AgentSettings, GrantForgeAgent> factory = settings -> agent;
    private final HBaseAgentRuntime runtime = new HBaseAgentRuntime(factory, () -> 0L);
    private final GrantForgeHBaseCoprocessor coprocessor = new GrantForgeHBaseCoprocessor(runtime);

    /** A hook of the coprocessor, called for a user. */
    @FunctionalInterface
    private interface Hook
    {
        void call(ObserverContext<?> ctx) throws Exception;
    }

    @BeforeAll
    static void superusers() throws Exception
    {
        Configuration configuration = new Configuration(false);
        configuration.set(Superusers.SUPERUSER_CONF_KEY, "admin");
        Superusers.initialize(configuration);
    }

    @BeforeEach
    void start() throws Exception
    {
        when(agent.snapshot()).thenAnswer(invocation -> snapshot.get());
        when(agent.record(any(AccessEvent.class))).thenAnswer(invocation -> events.add(invocation.getArgument(0)));
        coprocessor.start(environment(Map.of("grantforge.audit.fold.window.ms", "0")));
        snapshot.set(policies().snapshot());
    }

    @AfterEach
    void stop()
    {
        coprocessor.stop(mock(CoprocessorEnvironment.class));
    }

    private CoprocessorEnvironment environment(Map<String, String> extra) throws Exception
    {
        Configuration configuration = new Configuration(false);
        HBaseAgentRuntimeTest.settings(temporary).forEach(configuration::set);
        extra.forEach(configuration::set);
        configuration.set("hbase.unrelated", "x");
        CoprocessorEnvironment environment = mock(CoprocessorEnvironment.class);
        when(environment.getConfiguration()).thenReturn(configuration);
        return environment;
    }

    private static HBaseSnapshots policies()
    {
        return new HBaseSnapshots()
                .allow(List.of("alice"), List.of("read"), "namespace", "sales")
                .allow(List.of("bob"), List.of("read"), "namespace", "sales", "table", "orders", "column_family", "d")
                .allow(List.of("erin"), List.of("write"), "namespace", "sales", "table", "orders", "column_family", "d", "column", "*")
                .allow(List.of("dave"), List.of("create", "admin"), "namespace", "sales")
                .allow(List.of("ops"), List.of("admin"), "global", "*")
                .deny(List.of("mallory"), List.of("read"), "namespace", "sales");
    }

    private static ObserverContext<MasterCoprocessorEnvironment> master(@Nullable String user)
    {
        return new ObserverContextImpl<>(user == null ? null : User.createUserForTesting(new Configuration(false), user, new String[0]));
    }

    private static ObserverContext<RegionCoprocessorEnvironment> region(@Nullable String user, TableName table)
    {
        ObserverContextImpl<RegionCoprocessorEnvironment> ctx = new ObserverContextImpl<>(user == null ? null
                : User.createUserForTesting(new Configuration(false), user, new String[0]));
        RegionCoprocessorEnvironment environment = mock(RegionCoprocessorEnvironment.class);
        when(environment.getRegionInfo()).thenReturn(RegionInfoBuilder.newBuilder(table).build());
        ctx.prepare(environment);
        return ctx;
    }

    private static ObserverContext<RegionServerCoprocessorEnvironment> regionServer(String user)
    {
        return new ObserverContextImpl<>(User.createUserForTesting(new Configuration(false), user, new String[0]));
    }

    private static void allowed(String user, Hook hook) throws Exception
    {
        hook.call(master(user));
    }

    private static String refused(String user, Hook hook)
    {
        try {
            hook.call(master(user));
        }
        catch (AccessDeniedException denied) {
            return String.valueOf(denied.getMessage());
        }
        catch (Exception other) {
            throw new AssertionError(other);
        }
        throw new AssertionError("allowed");
    }

    @Test
    void startsTheSharedAgentOnceAndGivesItBack() throws Exception
    {
        assertThat(coprocessor.getMasterObserver()).containsSame(coprocessor);
        assertThat(coprocessor.getRegionObserver()).containsSame(coprocessor);
        assertThat(coprocessor.getRegionServerObserver()).containsSame(coprocessor);
        GrantForgeHBaseCoprocessor region = new GrantForgeHBaseCoprocessor(runtime);
        region.start(environment(Map.of()));
        region.stop(mock(CoprocessorEnvironment.class));
        region.stop(mock(CoprocessorEnvironment.class));
        assertThat(runtime.running()).as("the first coprocessor still holds it").isTrue();
        coprocessor.stop(mock(CoprocessorEnvironment.class));
        verify(agent).close();
        GrantForgeHBaseCoprocessor broken = new GrantForgeHBaseCoprocessor(new HBaseAgentRuntime(factory, () -> 0L));
        assertThatThrownBy(() -> broken.start(environment(Map.of("grantforge.decision.cache.size", "-1")))).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("GrantForge cannot start the HBase agent");
        assertThat(GrantForgeHBaseCoprocessor.version()).isNotBlank().doesNotContain("${");
        assertThat(new GrantForgeHBaseCoprocessor()).isNotNull();
    }

    @Test
    void leavesTheServersSuperusersAndAServiceNotInUseUnchecked() throws Exception
    {
        coprocessor.preDeleteTable(master(null), ORDERS);
        coprocessor.preDeleteTable(master("admin"), ORDERS);
        coprocessor.preShutdown(master("admin"));
        assertThat(refused("stranger", ctx -> coprocessor.preDeleteTable(master("stranger"), ORDERS))).contains("user stranger");
        snapshot.set(policies().disabled().snapshot());
        coprocessor.preDeleteTable(master("stranger"), ORDERS);
        coprocessor.preGetOp(region("stranger", ORDERS), new Get(Bytes.toBytes("r")), new ArrayList<>());
        assertThat(events).as("only the refusal is reported").hasSize(1);
    }

    @Test
    void givesANamespacesOwnerTheNamespaceAndItsTables() throws Exception
    {
        TableDescriptor table = TableDescriptorBuilder.newBuilder(ORDERS).build();
        RegionInfo region = RegionInfoBuilder.newBuilder(ORDERS).build();
        ServerName server = ServerName.valueOf("rs1", 16020, 1);
        SnapshotDescription saved = new SnapshotDescription("orders-1", ORDERS);
        List<Hook> hooks = List.of(
                ctx -> coprocessor.preCreateNamespace(master(null), NamespaceDescriptor.create("sales").build()),
                ctx -> coprocessor.preCreateTable(cast(ctx), table, new RegionInfo[] {region}),
                ctx -> coprocessor.preDeleteTable(cast(ctx), ORDERS),
                ctx -> coprocessor.preTruncateTable(cast(ctx), ORDERS),
                ctx -> assertThat(coprocessor.preModifyTable(cast(ctx), ORDERS, table, table)).isSameAs(table),
                ctx -> assertThat(coprocessor.preModifyTableStoreFileTracker(cast(ctx), ORDERS, "FILE")).isEqualTo("FILE"),
                ctx -> assertThat(coprocessor.preModifyColumnFamilyStoreFileTracker(cast(ctx), ORDERS, D, "FILE")).isEqualTo("FILE"),
                ctx -> coprocessor.preEnableTable(cast(ctx), ORDERS),
                ctx -> coprocessor.preDisableTable(cast(ctx), ORDERS),
                ctx -> coprocessor.preTableFlush(cast(ctx), ORDERS),
                ctx -> coprocessor.preSplitRegion(cast(ctx), ORDERS, Bytes.toBytes("m")),
                ctx -> coprocessor.preMergeRegions(cast(ctx), new RegionInfo[] {region, region}),
                ctx -> coprocessor.preMove(cast(ctx), region, server, server),
                ctx -> coprocessor.preAssign(cast(ctx), region),
                ctx -> coprocessor.preUnassign(cast(ctx), region),
                ctx -> coprocessor.preRegionOffline(cast(ctx), region),
                ctx -> coprocessor.preSnapshot(cast(ctx), saved, table),
                ctx -> coprocessor.preCloneSnapshot(cast(ctx), saved, table),
                ctx -> coprocessor.preRestoreSnapshot(cast(ctx), saved, table),
                ctx -> coprocessor.preDeleteSnapshot(cast(ctx), saved),
                ctx -> coprocessor.preListSnapshot(cast(ctx), saved),
                ctx -> coprocessor.preCreateNamespace(cast(ctx), NamespaceDescriptor.create("sales").build()),
                ctx -> coprocessor.preModifyNamespace(cast(ctx), NamespaceDescriptor.create("sales").build(), NamespaceDescriptor.create("sales").build()),
                ctx -> coprocessor.preDeleteNamespace(cast(ctx), "sales"));
        for (Hook hook : hooks) {
            hook.call(master("dave"));
        }
        for (Hook hook : hooks.subList(1, hooks.size())) {
            assertThat(refused("bob", hook)).contains("user bob has no ");
        }
        assertThat(refused("bob", ctx -> coprocessor.preCreateTable(cast(ctx), table, new RegionInfo[0])))
                .isEqualTo("Permission denied: user bob has no create on namespace sales (no GrantForge policy allows it)");
        assertThat(refused("alice", ctx -> coprocessor.preDeleteTable(cast(ctx), ORDERS))).contains("has no admin or create on table sales:orders");
        assertThat(refused("alice", ctx -> coprocessor.preListSnapshot(cast(ctx), saved))).contains("has no admin on table sales:orders");
    }

    @SuppressWarnings("unchecked")
    private static ObserverContext<MasterCoprocessorEnvironment> cast(ObserverContext<?> ctx)
    {
        return (ObserverContext<MasterCoprocessorEnvironment>) ctx;
    }

    @Test
    void leavesTheClusterToItsAdministrators() throws Exception
    {
        org.apache.hadoop.hbase.quotas.GlobalQuotaSettings quotas = mock(org.apache.hadoop.hbase.quotas.GlobalQuotaSettings.class);
        org.apache.hadoop.hbase.replication.ReplicationPeerConfig peer = mock(org.apache.hadoop.hbase.replication.ReplicationPeerConfig.class);
        ServerName server = ServerName.valueOf("rs1", 16020, 1);
        List<Hook> hooks = List.of(
                ctx -> coprocessor.preBalance(cast(ctx), org.apache.hadoop.hbase.client.BalanceRequest.defaultInstance()),
                ctx -> coprocessor.preBalanceSwitch(cast(ctx), false),
                ctx -> coprocessor.preShutdown(cast(ctx)),
                ctx -> coprocessor.preStopMaster(cast(ctx)),
                ctx -> coprocessor.preSetSplitOrMergeEnabled(cast(ctx), false, org.apache.hadoop.hbase.client.MasterSwitchType.SPLIT),
                ctx -> coprocessor.preClearDeadServers(cast(ctx)),
                ctx -> coprocessor.preDecommissionRegionServers(cast(ctx), List.of(server), false),
                ctx -> coprocessor.preRecommissionRegionServer(cast(ctx), server, List.of()),
                ctx -> coprocessor.preListDecommissionedRegionServers(cast(ctx)),
                ctx -> coprocessor.preAbortProcedure(cast(ctx), 1L),
                ctx -> coprocessor.preGetProcedures(cast(ctx)),
                ctx -> coprocessor.preGetLocks(cast(ctx)),
                ctx -> coprocessor.preSetUserQuota(cast(ctx), "u", quotas),
                ctx -> coprocessor.preSetUserQuota(cast(ctx), "u", ORDERS, quotas),
                ctx -> coprocessor.preSetUserQuota(cast(ctx), "u", "sales", quotas),
                ctx -> coprocessor.preSetTableQuota(cast(ctx), ORDERS, quotas),
                ctx -> coprocessor.preSetNamespaceQuota(cast(ctx), "sales", quotas),
                ctx -> coprocessor.preSetRegionServerQuota(cast(ctx), "rs1", quotas),
                ctx -> coprocessor.preSwitchRpcThrottle(cast(ctx), true),
                ctx -> coprocessor.preSwitchExceedThrottleQuota(cast(ctx), true),
                ctx -> coprocessor.preAddReplicationPeer(cast(ctx), "p", peer),
                ctx -> coprocessor.preRemoveReplicationPeer(cast(ctx), "p"),
                ctx -> coprocessor.preEnableReplicationPeer(cast(ctx), "p"),
                ctx -> coprocessor.preDisableReplicationPeer(cast(ctx), "p"),
                ctx -> coprocessor.preGetReplicationPeerConfig(cast(ctx), "p"),
                ctx -> coprocessor.preUpdateReplicationPeerConfig(cast(ctx), "p", peer),
                ctx -> coprocessor.preListReplicationPeers(cast(ctx), ".*"),
                ctx -> coprocessor.preAddRSGroup(cast(ctx), "g"),
                ctx -> coprocessor.preRemoveRSGroup(cast(ctx), "g"),
                ctx -> coprocessor.preRenameRSGroup(cast(ctx), "g", "h"),
                ctx -> coprocessor.preMoveServers(cast(ctx), java.util.Set.of(), "g"),
                ctx -> coprocessor.preMoveTables(cast(ctx), java.util.Set.of(ORDERS), "g"),
                ctx -> coprocessor.preMoveServersAndTables(cast(ctx), java.util.Set.of(), java.util.Set.of(), "g"),
                ctx -> coprocessor.preRemoveServers(cast(ctx), java.util.Set.of()),
                ctx -> coprocessor.preBalanceRSGroup(cast(ctx), "g", org.apache.hadoop.hbase.client.BalanceRequest.defaultInstance()),
                ctx -> coprocessor.preUpdateRSGroupConfig(cast(ctx), "g", Map.of()),
                ctx -> coprocessor.preStopRegionServer(regionServer(user(ctx))),
                ctx -> coprocessor.preRollWALWriterRequest(regionServer(user(ctx))),
                ctx -> coprocessor.preClearCompactionQueues(regionServer(user(ctx))));
        for (Hook hook : hooks) {
            allowed("ops", hook);
            assertThat(refused("dave", hook)).contains("user dave has no admin on global *");
        }
    }

    private static String user(ObserverContext<?> ctx)
    {
        return ctx.getCaller().orElseThrow().getShortName();
    }

    @Test
    void hidesFromListingsWhatAUserMayNotUse() throws Exception
    {
        List<NamespaceDescriptor> descriptors = new ArrayList<>(List.of(NamespaceDescriptor.create("sales").build(),
                NamespaceDescriptor.create("hr").build(), NamespaceDescriptor.create("hbase").build()));
        coprocessor.postListNamespaceDescriptors(master("bob"), descriptors);
        assertThat(descriptors).extracting(NamespaceDescriptor::getName).containsExactly("sales", "hbase");
        List<String> names = new ArrayList<>(List.of("sales", "hr", "hbase"));
        coprocessor.postListNamespaces(master("mallory"), names);
        assertThat(names).containsExactly("hbase");
        List<TableDescriptor> tables = new ArrayList<>(List.of(TableDescriptorBuilder.newBuilder(ORDERS).build(),
                TableDescriptorBuilder.newBuilder(TableName.valueOf("sales:items")).build(), TableDescriptorBuilder.newBuilder(META).build()));
        coprocessor.postGetTableDescriptors(master("bob"), List.of(), tables, ".*");
        assertThat(tables).extracting(TableDescriptor::getTableName).containsExactly(ORDERS, META);
        List<TableDescriptor> again = new ArrayList<>(List.of(TableDescriptorBuilder.newBuilder(TableName.valueOf("sales:items")).build()));
        coprocessor.postGetTableNames(master("alice"), again, ".*");
        assertThat(again).hasSize(1);
        coprocessor.postGetTableNames(master("admin"), again, ".*");
        snapshot.set(policies().disabled().snapshot());
        coprocessor.postGetTableNames(master("stranger"), again, ".*");
        assertThat(again).hasSize(1);
        assertThat(events).as("listings are not accesses").isEmpty();
    }

    @Test
    void describesOnlyNamespacesAUserMayUse() throws Exception
    {
        coprocessor.preGetNamespaceDescriptor(master("bob"), "sales");
        coprocessor.preGetNamespaceDescriptor(master("bob"), "hbase");
        coprocessor.preGetNamespaceDescriptor(master(null), "hr");
        assertThat(refused("bob", ctx -> coprocessor.preGetNamespaceDescriptor(cast(ctx), "hr"))).contains("may use nothing in namespace hr");
    }

    @Test
    void readsWholeTablesUnfilteredAndPartsOfTablesFiltered() throws Exception
    {
        snapshot.set(new HBaseSnapshots().allow(List.of("alice"), List.of("read"), "namespace", "sales").snapshot());
        Get get = new Get(Bytes.toBytes("r1"));
        coprocessor.preGetOp(region("alice", ORDERS), get, new ArrayList<>());
        assertThat(get.getFilter()).as("alice may read the whole table, and no policy names columns").isNull();
        snapshot.set(policies().snapshot());
        Scan scan = new Scan();
        coprocessor.preScannerOpen(region("bob", ORDERS), scan);
        assertThat(scan.getFilter()).isInstanceOf(ColumnFilter.class);
        assertThat(scan.getFilter().filterCell(new KeyValue(Bytes.toBytes("r1"), D, Bytes.toBytes("name"), Bytes.toBytes("v"))))
                .isEqualTo(Filter.ReturnCode.INCLUDE);
        assertThat(scan.getFilter().filterCell(new KeyValue(Bytes.toBytes("r1"), Bytes.toBytes("meta"), Bytes.toBytes("x"), Bytes.toBytes("v"))))
                .isEqualTo(Filter.ReturnCode.NEXT_COL);
        Get keyOnly = new Get(Bytes.toBytes("r1")).setFilter(new KeyOnlyFilter());
        coprocessor.preGetOp(region("bob", ORDERS), keyOnly, new ArrayList<>());
        assertThat(keyOnly.getFilter()).as("the user's own filter stays").isInstanceOf(FilterList.class);
        Map<String, @Nullable Object> partial = EventFields.of(events.get(events.size() - 1));
        assertThat(partial).containsEntry("user", "bob").containsEntry("resource", "sales:orders").containsEntry("outcome", "ALLOWED")
                .containsEntry("request", "only the columns the user may read").containsEntry("action", "get");
        assertThatThrownBy(() -> coprocessor.preScannerOpen(region("mallory", ORDERS), new Scan())).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("GrantForge policy 6 denies it");
        assertThatThrownBy(() -> coprocessor.preGetOp(region("stranger", ORDERS), new Get(Bytes.toBytes("r1")), new ArrayList<>()))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("no GrantForge policy allows it");
        coprocessor.preScannerOpen(region("stranger", META), new Scan());
        coprocessor.preScannerOpen(region(null, ORDERS), new Scan());
    }

    @Test
    void filtersEvenAWholeTableWhenPoliciesNameColumns() throws Exception
    {
        snapshot.set(policies().deny(List.of("alice"), List.of("read"), "namespace", "sales", "table", "orders", "column_family", "d", "column",
                "ssn").snapshot());
        Scan scan = new Scan();
        coprocessor.preScannerOpen(region("alice", ORDERS), scan);
        assertThat(scan.getFilter().filterCell(new KeyValue(Bytes.toBytes("r1"), D, Bytes.toBytes("ssn"), Bytes.toBytes("v"))))
                .isEqualTo(Filter.ReturnCode.NEXT_COL);
        assertThat(scan.getFilter().filterCell(new KeyValue(Bytes.toBytes("r1"), D, Bytes.toBytes(""), Bytes.toBytes("v"))))
                .as("an empty qualifier is decided at its family").isEqualTo(Filter.ReturnCode.INCLUDE);
        assertThat(EventFields.of(events.get(events.size() - 1))).containsEntry("request", null);
    }

    @SuppressWarnings("unchecked")
    private static MiniBatchOperationInProgress<Mutation> batch(Mutation... mutations)
    {
        MiniBatchOperationInProgress<Mutation> batch = mock(MiniBatchOperationInProgress.class);
        when(batch.size()).thenReturn(mutations.length);
        for (int index = 0; index < mutations.length; index++) {
            when(batch.getOperation(index)).thenReturn(mutations[index]);
        }
        return batch;
    }

    private static Put put(String family, String qualifier)
    {
        return new Put(Bytes.toBytes("r1")).addColumn(Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes("v"));
    }

    @Test
    void writesTheColumnsAPolicyNames() throws Exception
    {
        coprocessor.preBatchMutate(region("erin", ORDERS), batch(put("d", "name"), put("d", "ssn")));
        Map<String, @Nullable Object> written = EventFields.of(events.get(events.size() - 1));
        assertThat(written).containsEntry("user", "erin").containsEntry("resource", "sales:orders").containsEntry("accessType", "write")
                .containsEntry("action", "put").containsEntry("request", "2 columns").containsEntry("outcome", "ALLOWED");
        coprocessor.preBatchMutate(region("erin", ORDERS), batch(put("d", "name"), new Delete(Bytes.toBytes("r1")).addFamily(D)));
        assertThat(EventFields.of(events.get(events.size() - 1))).containsEntry("action", "batch").containsEntry("request", "2 columns");
        assertThatThrownBy(() -> coprocessor.preBatchMutate(region("erin", ORDERS), batch(put("meta", "x")))).isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("user erin has no write on column sales:orders/meta:x");
        assertThat(EventFields.of(events.get(events.size() - 1))).containsEntry("outcome", "DENIED").containsEntry("resource", "sales:orders/meta:x");
        assertThatThrownBy(() -> coprocessor.preBatchMutate(region("erin", ORDERS), batch(new Delete(Bytes.toBytes("r1")))))
                .hasMessageContaining("no write on table sales:orders");
        coprocessor.preBatchMutate(region(null, ORDERS), batch(put("meta", "x")));
        snapshot.set(policies().disabled().snapshot());
        coprocessor.preBatchMutate(region("stranger", ORDERS), batch(put("meta", "x")));
    }

    @Test
    void namesWhatEachMutationWrites() throws Exception
    {
        HBaseResource table = HBaseResource.table(ORDERS);
        assertThat(GrantForgeHBaseCoprocessor.writes(table, put("d", "name"))).containsExactly(table.family("d").column("name"));
        assertThat(GrantForgeHBaseCoprocessor.writes(table, put("d", ""))).containsExactly(table.family("d"));
        assertThat(GrantForgeHBaseCoprocessor.writes(table, new Delete(Bytes.toBytes("r1")))).containsExactly(table);
        assertThat(GrantForgeHBaseCoprocessor.writes(table, new Delete(Bytes.toBytes("r1")).addFamily(D))).containsExactly(table.family("d"));
        assertThat(GrantForgeHBaseCoprocessor.writes(table, new Delete(Bytes.toBytes("r1")).addFamilyVersion(D, 1L)))
                .containsExactly(table.family("d"));
        assertThat(GrantForgeHBaseCoprocessor.writes(table, new Increment(Bytes.toBytes("r1")).addColumn(D, Bytes.toBytes("n"), 1L)))
                .containsExactly(table.family("d").column("n"));
        coprocessor.preBatchMutate(region("erin", ORDERS), batch(new Increment(Bytes.toBytes("r1")).addColumn(D, Bytes.toBytes("n"), 1L)));
        assertThat(EventFields.of(events.get(events.size() - 1))).containsEntry("action", "increment").containsEntry("request", "1 column");
        coprocessor.preBatchMutate(region("erin", ORDERS), batch(new Append(Bytes.toBytes("r1")).addColumn(D, Bytes.toBytes("n"), Bytes.toBytes("x"))));
        assertThat(EventFields.of(events.get(events.size() - 1))).containsEntry("action", "append");
        snapshot.set(policies().allow(List.of("erin"), List.of("write"), "namespace", "sales").snapshot());
        coprocessor.preBatchMutate(region("erin", ORDERS), batch(new Delete(Bytes.toBytes("r1"))));
        assertThat(EventFields.of(events.get(events.size() - 1))).containsEntry("action", "delete").containsEntry("request", null);
        coprocessor.preBatchMutate(region("erin", ORDERS), batch());
    }

    @Test
    void readsWhatACheckAndMutateCompares() throws Exception
    {
        CheckAndMutate byColumn = CheckAndMutate.newBuilder(Bytes.toBytes("r1")).ifEquals(D, Bytes.toBytes("name"), Bytes.toBytes("Ann"))
                .build(put("d", "name"));
        CheckAndMutateResult result = new CheckAndMutateResult(true, null);
        assertThat(coprocessor.preCheckAndMutate(region("bob", ORDERS), byColumn, result)).isSameAs(result);
        CheckAndMutate byFilter = CheckAndMutate.newBuilder(Bytes.toBytes("r1")).ifMatches(new KeyOnlyFilter()).build(put("d", "name"));
        assertThatThrownBy(() -> coprocessor.preCheckAndMutate(region("bob", ORDERS), byFilter, result)).as("a filter compares the whole row")
                .hasMessageContaining("no read on table sales:orders");
        coprocessor.preCheckAndMutate(region("alice", ORDERS), byFilter, result);
    }

    @Test
    void leavesBulkLoadsFlushesAndCompactionsToCreatorsAndAdministrators() throws Exception
    {
        Store store = mock(Store.class);
        coprocessor.preBulkLoadHFile(region("dave", ORDERS), List.of());
        coprocessor.preFlush(region("dave", ORDERS), FlushLifeCycleTracker.DUMMY);
        coprocessor.preCompactSelection(region("dave", ORDERS), store, List.of(), CompactionLifeCycleTracker.DUMMY);
        coprocessor.preFlush(region(null, ORDERS), FlushLifeCycleTracker.DUMMY);
        assertThatThrownBy(() -> coprocessor.preBulkLoadHFile(region("alice", ORDERS), List.of())).hasMessageContaining("no create on table");
        assertThatThrownBy(() -> coprocessor.preFlush(region("alice", ORDERS), FlushLifeCycleTracker.DUMMY))
                .hasMessageContaining("no admin or create on table");
        assertThatThrownBy(() -> coprocessor.preCompactSelection(region("alice", ORDERS), store, List.of(), CompactionLifeCycleTracker.DUMMY))
                .hasMessageContaining("no admin or create on table");
    }

    @Test
    void reportsEachDecisionWithTheClientAndThePolicy() throws Exception
    {
        coprocessor.preDeleteTable(master("dave"), ORDERS);
        Map<String, @Nullable Object> event = EventFields.of(events.get(0));
        assertThat(event).containsEntry("user", "dave").containsEntry("resource", "sales:orders").containsEntry("resourceType", "table")
                .containsEntry("accessType", "admin").containsEntry("action", "deleteTable").containsEntry("outcome", "ALLOWED")
                .containsEntry("policyId", "4").containsEntry("enforcer", "GRANTFORGE");
        assertThatCode(() -> coprocessor.preEnableTable(master("dave"), ORDERS)).doesNotThrowAnyException();
    }

    @Test
    void refusesEverythingWithoutASnapshot() throws Exception
    {
        snapshot.set(null);
        assertThat(refused("alice", ctx -> coprocessor.preDeleteTable(cast(ctx), ORDERS))).contains("no GrantForge policy allows it");
        assertThatThrownBy(() -> coprocessor.preGetOp(region("alice", ORDERS), new Get(Bytes.toBytes("r1")), new ArrayList<>()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
