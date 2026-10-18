// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Append;
import org.apache.hadoop.hbase.client.BalanceRequest;
import org.apache.hadoop.hbase.client.CheckAndMutate;
import org.apache.hadoop.hbase.client.CheckAndMutateResult;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.MasterSwitchType;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.SnapshotDescription;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.coprocessor.MasterCoprocessor;
import org.apache.hadoop.hbase.coprocessor.MasterCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.MasterObserver;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessor;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.RegionObserver;
import org.apache.hadoop.hbase.coprocessor.RegionServerCoprocessor;
import org.apache.hadoop.hbase.coprocessor.RegionServerCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.RegionServerObserver;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.ipc.RpcServer;
import org.apache.hadoop.hbase.net.Address;
import org.apache.hadoop.hbase.quotas.GlobalQuotaSettings;
import org.apache.hadoop.hbase.regionserver.FlushLifeCycleTracker;
import org.apache.hadoop.hbase.regionserver.MiniBatchOperationInProgress;
import org.apache.hadoop.hbase.regionserver.Store;
import org.apache.hadoop.hbase.regionserver.compactions.CompactionLifeCycleTracker;
import org.apache.hadoop.hbase.replication.ReplicationPeerConfig;
import org.apache.hadoop.hbase.security.AccessDeniedException;
import org.apache.hadoop.hbase.security.Superusers;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.hbase.util.Bytes;
import org.devlive.grantforge.agent.AccessEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The GrantForge coprocessor of HBase masters and region servers; name it in {@code hbase.coprocessor.master.classes},
 * {@code hbase.coprocessor.region.classes} and {@code hbase.coprocessor.regionserver.classes}. It checks table, namespace
 * and cluster operations, reads and writes against the policies of the service's signed snapshot, each at every scope of
 * HBase's permissions it names, and reports each decision as an access event. Beyond refusing what is not allowed it
 * <ul>
 *   <li>gives a namespace's owners its tables, from creating them to administering them, without naming them;</li>
 *   <li>returns the columns of a read the user may read rather than failing the read, when the user may read some of
 *   them;</li>
 *   <li>hides from listings the namespaces, tables and snapshots the user may not use;</li>
 *   <li>tells in each refusal what was missing and whether a policy denied it;</li>
 *   <li>keeps the decisions of the current snapshot and folds identical allowed accesses into one event per window.</li>
 * </ul>
 *
 * <p>HBase's superusers, and the servers themselves, are not checked. Tables of the {@code hbase} namespace, such as
 * {@code hbase:meta}, may be read by everyone, as clients must. A service that is not in use leaves HBase unchecked, as it
 * is without an authorizer; without a snapshot, everything else is refused.
 */
public final class GrantForgeHBaseCoprocessor
        implements MasterCoprocessor, RegionCoprocessor, RegionServerCoprocessor, MasterObserver, RegionObserver, RegionServerObserver
{
    static final String READ = "read";
    static final String WRITE = "write";
    static final String CREATE = "create";
    static final String ADMIN = "admin";
    private static final String[] ANY = {READ, WRITE, CREATE, ADMIN};
    private static final String SYSTEM_NAMESPACE = NamespaceDescriptor.SYSTEM_NAMESPACE_NAME_STR;

    private static final Logger LOG = LoggerFactory.getLogger(GrantForgeHBaseCoprocessor.class);
    private static final HBaseAgentRuntime RUNTIME = new HBaseAgentRuntime();

    private final HBaseAgentRuntime runtime;
    private final AtomicBoolean acquired = new AtomicBoolean();

    /** Creates the coprocessor HBase loads, with the agent of the process. */
    public GrantForgeHBaseCoprocessor()
    {
        this(RUNTIME);
    }

    GrantForgeHBaseCoprocessor(HBaseAgentRuntime runtime)
    {
        this.runtime = runtime;
    }

    @Override
    public Optional<MasterObserver> getMasterObserver()
    {
        return Optional.of(this);
    }

    @Override
    public Optional<RegionObserver> getRegionObserver()
    {
        return Optional.of(this);
    }

    @Override
    public Optional<RegionServerObserver> getRegionServerObserver()
    {
        return Optional.of(this);
    }

    @Override
    public void start(CoprocessorEnvironment env) throws IOException
    {
        Map<String, String> settings = new HashMap<>();
        for (Map.Entry<String, String> entry : env.getConfiguration()) {
            if (entry.getKey().startsWith(HBaseAgentRuntime.PREFIX)) {
                settings.put(entry.getKey(), entry.getValue());
            }
        }
        try {
            if (runtime.acquire(settings, version())) {
                LOG.info("The GrantForge HBase agent started");
            }
        }
        catch (IllegalArgumentException invalid) {
            throw new IOException("GrantForge cannot start the HBase agent: " + invalid.getMessage(), invalid);
        }
        acquired.set(true);
    }

    @Override
    public void stop(CoprocessorEnvironment env)
    {
        if (acquired.getAndSet(false)) {
            runtime.release();
        }
    }

    /**
     * Returns the agent's version.
     *
     * @return the GrantForge version it was built with
     */
    static String version()
    {
        Properties properties = new Properties();
        try (InputStream in = GrantForgeHBaseCoprocessor.class.getResourceAsStream("/META-INF/grantforge/hbase-agent-version.properties")) {
            if (in != null) {
                properties.load(in);
            }
        }
        catch (IOException unreadable) {
            LOG.debug("The agent version cannot be read", unreadable);
        }
        return properties.getProperty("grantforge.version", "unknown");
    }

    // Checks

    /** The caller of a request, or {@code null} for the servers' own work and superusers, which are not checked. */
    private static @Nullable Caller caller(ObserverContext<?> ctx)
    {
        Optional<User> found = ctx.getCaller();
        if (!found.isPresent() || Superusers.isSuperUser(found.get())) {
            return null;
        }
        User user = found.get();
        Optional<InetAddress> address = RpcServer.getRemoteAddress();
        return new Caller(user.getShortName(), Arrays.asList(user.getGroupNames()), address.isPresent() ? address.get().getHostAddress() : null);
    }

    /** Refuses a request unless the caller may do one of the access types to the resource. */
    private void require(ObserverContext<?> ctx, String action, HBaseResource resource, String... accessTypes) throws AccessDeniedException
    {
        Caller caller = caller(ctx);
        if (caller == null) {
            return;
        }
        Policies policies = runtime.policies();
        if (!policies.enforced()) {
            return;
        }
        Policies.Verdict verdict = policies.decide(caller, resource, accessTypes);
        report(caller, resource, verdict, verdict.allowed(), action, null);
        if (!verdict.allowed()) {
            throw refused(caller, resource, verdict, accessTypes);
        }
    }

    private void report(Caller caller, HBaseResource resource, Policies.Verdict verdict, boolean allowed, String action, @Nullable String request)
    {
        runtime.audit(AccessEvent.builder(caller.user(), resource.display(), verdict.accessType(), allowed).decidedBy(verdict.decision())
                .enforcedByGrantForge().clientIp(caller.clientIp()).resourceType(resource.level()).action(action).request(request).build());
    }

    /**
     * Words a refusal: what the user lacks on what, and whether a policy denied it or none allowed it.
     *
     * @param caller who asked
     * @param resource the resource
     * @param verdict the verdict
     * @param accessTypes the access types any of which would have done
     * @return the exception HBase returns to the client
     */
    static AccessDeniedException refused(Caller caller, HBaseResource resource, Policies.Verdict verdict, String... accessTypes)
    {
        Long policy = verdict.decision().policyId();
        String why = verdict.denied() && policy != null ? "GrantForge policy " + policy + " denies it" : "no GrantForge policy allows it";
        return new AccessDeniedException("Permission denied: user " + caller.user() + " has no " + String.join(" or ", accessTypes) + " on "
                + resource.level().replace('_', ' ') + " " + resource.display() + " (" + why + ")");
    }

    // HBase 3 declares the region hooks with ObserverContext<? extends RegionCoprocessorEnvironment>, HBase 2 with
    // ObserverContext<RegionCoprocessorEnvironment>; the agent declares their erasure, which overrides both.
    @SuppressWarnings("rawtypes")
    private static HBaseResource table(ObserverContext ctx)
    {
        return HBaseResource.table(((RegionCoprocessorEnvironment) ctx.getEnvironment()).getRegionInfo().getTable());
    }

    // Master: namespaces

    @Override
    public void preCreateNamespace(ObserverContext<MasterCoprocessorEnvironment> ctx, NamespaceDescriptor ns) throws IOException
    {
        require(ctx, "createNamespace", HBaseResource.namespace(ns.getName()), ADMIN);
    }

    @Override
    public void preDeleteNamespace(ObserverContext<MasterCoprocessorEnvironment> ctx, String namespace) throws IOException
    {
        require(ctx, "deleteNamespace", HBaseResource.namespace(namespace), ADMIN);
    }

    @Override
    public void preModifyNamespace(ObserverContext<MasterCoprocessorEnvironment> ctx, NamespaceDescriptor currentNsDescriptor,
            NamespaceDescriptor newNsDescriptor) throws IOException
    {
        require(ctx, "modifyNamespace", HBaseResource.namespace(newNsDescriptor.getName()), ADMIN);
    }

    @Override
    public void preGetNamespaceDescriptor(ObserverContext<MasterCoprocessorEnvironment> ctx, String namespace) throws IOException
    {
        Caller caller = caller(ctx);
        Policies policies = runtime.policies();
        if (caller != null && policies.enforced() && !SYSTEM_NAMESPACE.equals(namespace)
                && !policies.uses(caller, HBaseResource.namespace(namespace), ANY)) {
            throw new AccessDeniedException("Permission denied: user " + caller.user() + " may use nothing in namespace " + namespace);
        }
    }

    @Override
    public void postListNamespaceDescriptors(ObserverContext<MasterCoprocessorEnvironment> ctx, List<NamespaceDescriptor> descriptors)
    {
        hide(ctx, descriptors, descriptor -> HBaseResource.namespace(descriptor.getName()));
    }

    @Override
    public void postListNamespaces(ObserverContext<MasterCoprocessorEnvironment> ctx, List<String> namespaces)
    {
        hide(ctx, namespaces, HBaseResource::namespace);
    }

    /** Leaves out of a listing what the caller may not use; the system namespace stays, as clients read it. */
    private <T> void hide(ObserverContext<?> ctx, List<T> listed, java.util.function.Function<T, HBaseResource> resource)
    {
        Caller caller = caller(ctx);
        if (caller == null) {
            return;
        }
        Policies policies = runtime.policies();
        if (!policies.enforced()) {
            return;
        }
        Iterator<T> each = listed.iterator();
        while (each.hasNext()) {
            HBaseResource item = resource.apply(each.next());
            if (!SYSTEM_NAMESPACE.equals(item.namespace()) && !policies.uses(caller, item, ANY)) {
                each.remove();
            }
        }
    }

    // Master: tables

    @Override
    public void preCreateTable(ObserverContext<MasterCoprocessorEnvironment> ctx, TableDescriptor desc, RegionInfo[] regions) throws IOException
    {
        require(ctx, "createTable", HBaseResource.namespace(desc.getTableName().getNamespaceAsString()), CREATE);
    }

    @Override
    public void preDeleteTable(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName) throws IOException
    {
        require(ctx, "deleteTable", HBaseResource.table(tableName), ADMIN, CREATE);
    }

    @Override
    public void preTruncateTable(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName) throws IOException
    {
        require(ctx, "truncateTable", HBaseResource.table(tableName), ADMIN, CREATE);
    }

    @Override
    public TableDescriptor preModifyTable(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName, TableDescriptor currentDescriptor,
            TableDescriptor newDescriptor) throws IOException
    {
        require(ctx, "modifyTable", HBaseResource.table(tableName), ADMIN, CREATE);
        return newDescriptor;
    }

    @Override
    public String preModifyTableStoreFileTracker(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName, String dstSFT)
            throws IOException
    {
        require(ctx, "modifyTableStoreFileTracker", HBaseResource.table(tableName), ADMIN, CREATE);
        return dstSFT;
    }

    @Override
    public String preModifyColumnFamilyStoreFileTracker(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName, byte[] family,
            String dstSFT) throws IOException
    {
        require(ctx, "modifyColumnFamilyStoreFileTracker", HBaseResource.table(tableName), ADMIN, CREATE);
        return dstSFT;
    }

    @Override
    public void preEnableTable(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName) throws IOException
    {
        require(ctx, "enableTable", HBaseResource.table(tableName), ADMIN, CREATE);
    }

    @Override
    public void preDisableTable(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName) throws IOException
    {
        require(ctx, "disableTable", HBaseResource.table(tableName), ADMIN, CREATE);
    }

    @Override
    public void preTableFlush(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName) throws IOException
    {
        require(ctx, "flushTable", HBaseResource.table(tableName), ADMIN, CREATE);
    }

    @Override
    public void preSplitRegion(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName, byte[] splitRow) throws IOException
    {
        require(ctx, "splitRegion", HBaseResource.table(tableName), ADMIN, CREATE);
    }

    @Override
    public void preMergeRegions(ObserverContext<MasterCoprocessorEnvironment> ctx, RegionInfo[] regionsToMerge) throws IOException
    {
        require(ctx, "mergeRegions", HBaseResource.table(regionsToMerge[0].getTable()), ADMIN, CREATE);
    }

    @Override
    public void preMove(ObserverContext<MasterCoprocessorEnvironment> ctx, RegionInfo region, ServerName srcServer, ServerName destServer)
            throws IOException
    {
        require(ctx, "move", HBaseResource.table(region.getTable()), ADMIN);
    }

    @Override
    public void preAssign(ObserverContext<MasterCoprocessorEnvironment> ctx, RegionInfo regionInfo) throws IOException
    {
        require(ctx, "assign", HBaseResource.table(regionInfo.getTable()), ADMIN);
    }

    @Override
    public void preUnassign(ObserverContext<MasterCoprocessorEnvironment> ctx, RegionInfo regionInfo) throws IOException
    {
        require(ctx, "unassign", HBaseResource.table(regionInfo.getTable()), ADMIN);
    }

    @Override
    public void preRegionOffline(ObserverContext<MasterCoprocessorEnvironment> ctx, RegionInfo regionInfo) throws IOException
    {
        require(ctx, "regionOffline", HBaseResource.table(regionInfo.getTable()), ADMIN);
    }

    @Override
    public void postGetTableDescriptors(ObserverContext<MasterCoprocessorEnvironment> ctx, List<TableName> tableNamesList,
            List<TableDescriptor> descriptors, String regex)
    {
        hide(ctx, descriptors, descriptor -> HBaseResource.table(descriptor.getTableName()));
    }

    @Override
    public void postGetTableNames(ObserverContext<MasterCoprocessorEnvironment> ctx, List<TableDescriptor> descriptors, String regex)
    {
        hide(ctx, descriptors, descriptor -> HBaseResource.table(descriptor.getTableName()));
    }

    // Master: snapshots

    @Override
    public void preSnapshot(ObserverContext<MasterCoprocessorEnvironment> ctx, SnapshotDescription snapshot, TableDescriptor tableDescriptor)
            throws IOException
    {
        require(ctx, "snapshot", HBaseResource.table(tableDescriptor.getTableName()), ADMIN);
    }

    @Override
    public void preCloneSnapshot(ObserverContext<MasterCoprocessorEnvironment> ctx, SnapshotDescription snapshot, TableDescriptor tableDescriptor)
            throws IOException
    {
        require(ctx, "cloneSnapshot", HBaseResource.table(snapshot.getTableName()), ADMIN);
        require(ctx, "cloneSnapshot", HBaseResource.namespace(tableDescriptor.getTableName().getNamespaceAsString()), CREATE);
    }

    @Override
    public void preRestoreSnapshot(ObserverContext<MasterCoprocessorEnvironment> ctx, SnapshotDescription snapshot,
            TableDescriptor tableDescriptor) throws IOException
    {
        require(ctx, "restoreSnapshot", HBaseResource.table(tableDescriptor.getTableName()), ADMIN);
    }

    @Override
    public void preDeleteSnapshot(ObserverContext<MasterCoprocessorEnvironment> ctx, SnapshotDescription snapshot) throws IOException
    {
        require(ctx, "deleteSnapshot", HBaseResource.table(snapshot.getTableName()), ADMIN);
    }

    /** HBase leaves out of a listing the snapshots this refuses. */
    @Override
    public void preListSnapshot(ObserverContext<MasterCoprocessorEnvironment> ctx, SnapshotDescription snapshot) throws IOException
    {
        Caller caller = caller(ctx);
        Policies policies = runtime.policies();
        if (caller == null || !policies.enforced()) {
            return;
        }
        HBaseResource table = HBaseResource.table(snapshot.getTableName());
        Policies.Verdict verdict = policies.decide(caller, table, ADMIN);
        if (!verdict.allowed()) {
            throw refused(caller, table, verdict, ADMIN);
        }
    }

    // Master: the cluster

    private void global(ObserverContext<?> ctx, String action) throws AccessDeniedException
    {
        require(ctx, action, HBaseResource.global(), ADMIN);
    }

    @Override
    public void preBalance(ObserverContext<MasterCoprocessorEnvironment> ctx, BalanceRequest request) throws IOException
    {
        global(ctx, "balance");
    }

    @Override
    public void preBalanceSwitch(ObserverContext<MasterCoprocessorEnvironment> ctx, boolean newValue) throws IOException
    {
        global(ctx, "balanceSwitch");
    }

    @Override
    public void preShutdown(ObserverContext<MasterCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "shutdown");
    }

    @Override
    public void preStopMaster(ObserverContext<MasterCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "stopMaster");
    }

    @Override
    public void preSetSplitOrMergeEnabled(ObserverContext<MasterCoprocessorEnvironment> ctx, boolean newValue, MasterSwitchType switchType)
            throws IOException
    {
        global(ctx, "setSplitOrMergeEnabled");
    }

    @Override
    public void preClearDeadServers(ObserverContext<MasterCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "clearDeadServers");
    }

    @Override
    public void preDecommissionRegionServers(ObserverContext<MasterCoprocessorEnvironment> ctx, List<ServerName> servers, boolean offload)
            throws IOException
    {
        global(ctx, "decommissionRegionServers");
    }

    @Override
    public void preRecommissionRegionServer(ObserverContext<MasterCoprocessorEnvironment> ctx, ServerName server, List<byte[]> encodedRegionNames)
            throws IOException
    {
        global(ctx, "recommissionRegionServer");
    }

    @Override
    public void preListDecommissionedRegionServers(ObserverContext<MasterCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "listDecommissionedRegionServers");
    }

    @Override
    public void preAbortProcedure(ObserverContext<MasterCoprocessorEnvironment> ctx, long procId) throws IOException
    {
        global(ctx, "abortProcedure");
    }

    @Override
    public void preGetProcedures(ObserverContext<MasterCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "getProcedures");
    }

    @Override
    public void preGetLocks(ObserverContext<MasterCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "getLocks");
    }

    @Override
    public void preSetUserQuota(ObserverContext<MasterCoprocessorEnvironment> ctx, String userName, GlobalQuotaSettings quotas) throws IOException
    {
        global(ctx, "setUserQuota");
    }

    @Override
    public void preSetUserQuota(ObserverContext<MasterCoprocessorEnvironment> ctx, String userName, TableName tableName, GlobalQuotaSettings quotas)
            throws IOException
    {
        global(ctx, "setUserQuota");
    }

    @Override
    public void preSetUserQuota(ObserverContext<MasterCoprocessorEnvironment> ctx, String userName, String namespace, GlobalQuotaSettings quotas)
            throws IOException
    {
        global(ctx, "setUserQuota");
    }

    @Override
    public void preSetTableQuota(ObserverContext<MasterCoprocessorEnvironment> ctx, TableName tableName, GlobalQuotaSettings quotas)
            throws IOException
    {
        global(ctx, "setTableQuota");
    }

    @Override
    public void preSetNamespaceQuota(ObserverContext<MasterCoprocessorEnvironment> ctx, String namespace, GlobalQuotaSettings quotas)
            throws IOException
    {
        global(ctx, "setNamespaceQuota");
    }

    @Override
    public void preSetRegionServerQuota(ObserverContext<MasterCoprocessorEnvironment> ctx, String regionServer, GlobalQuotaSettings quotas)
            throws IOException
    {
        global(ctx, "setRegionServerQuota");
    }

    @Override
    public void preSwitchRpcThrottle(ObserverContext<MasterCoprocessorEnvironment> ctx, boolean enable) throws IOException
    {
        global(ctx, "switchRpcThrottle");
    }

    @Override
    public void preSwitchExceedThrottleQuota(ObserverContext<MasterCoprocessorEnvironment> ctx, boolean enable) throws IOException
    {
        global(ctx, "switchExceedThrottleQuota");
    }

    @Override
    public void preAddReplicationPeer(ObserverContext<MasterCoprocessorEnvironment> ctx, String peerId, ReplicationPeerConfig peerConfig)
            throws IOException
    {
        global(ctx, "addReplicationPeer");
    }

    @Override
    public void preRemoveReplicationPeer(ObserverContext<MasterCoprocessorEnvironment> ctx, String peerId) throws IOException
    {
        global(ctx, "removeReplicationPeer");
    }

    @Override
    public void preEnableReplicationPeer(ObserverContext<MasterCoprocessorEnvironment> ctx, String peerId) throws IOException
    {
        global(ctx, "enableReplicationPeer");
    }

    @Override
    public void preDisableReplicationPeer(ObserverContext<MasterCoprocessorEnvironment> ctx, String peerId) throws IOException
    {
        global(ctx, "disableReplicationPeer");
    }

    @Override
    public void preGetReplicationPeerConfig(ObserverContext<MasterCoprocessorEnvironment> ctx, String peerId) throws IOException
    {
        global(ctx, "getReplicationPeerConfig");
    }

    @Override
    public void preUpdateReplicationPeerConfig(ObserverContext<MasterCoprocessorEnvironment> ctx, String peerId, ReplicationPeerConfig peerConfig)
            throws IOException
    {
        global(ctx, "updateReplicationPeerConfig");
    }

    @Override
    public void preListReplicationPeers(ObserverContext<MasterCoprocessorEnvironment> ctx, String regex) throws IOException
    {
        global(ctx, "listReplicationPeers");
    }

    @Override
    public void preAddRSGroup(ObserverContext<MasterCoprocessorEnvironment> ctx, String name) throws IOException
    {
        global(ctx, "addRSGroup");
    }

    @Override
    public void preRemoveRSGroup(ObserverContext<MasterCoprocessorEnvironment> ctx, String name) throws IOException
    {
        global(ctx, "removeRSGroup");
    }

    @Override
    public void preRenameRSGroup(ObserverContext<MasterCoprocessorEnvironment> ctx, String oldName, String newName) throws IOException
    {
        global(ctx, "renameRSGroup");
    }

    @Override
    public void preMoveServers(ObserverContext<MasterCoprocessorEnvironment> ctx, Set<Address> servers, String targetGroup) throws IOException
    {
        global(ctx, "moveServers");
    }

    @Override
    public void preMoveTables(ObserverContext<MasterCoprocessorEnvironment> ctx, Set<TableName> tables, String targetGroup) throws IOException
    {
        global(ctx, "moveTables");
    }

    @Override
    public void preMoveServersAndTables(ObserverContext<MasterCoprocessorEnvironment> ctx, Set<Address> servers, Set<TableName> tables,
            String targetGroup) throws IOException
    {
        global(ctx, "moveServersAndTables");
    }

    @Override
    public void preRemoveServers(ObserverContext<MasterCoprocessorEnvironment> ctx, Set<Address> servers) throws IOException
    {
        global(ctx, "removeServers");
    }

    @Override
    public void preBalanceRSGroup(ObserverContext<MasterCoprocessorEnvironment> ctx, String groupName, BalanceRequest request) throws IOException
    {
        global(ctx, "balanceRSGroup");
    }

    @Override
    public void preUpdateRSGroupConfig(ObserverContext<MasterCoprocessorEnvironment> ctx, String groupName, Map<String, String> configuration)
            throws IOException
    {
        global(ctx, "updateRSGroupConfig");
    }

    // Region server

    @Override
    public void preStopRegionServer(ObserverContext<RegionServerCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "stopRegionServer");
    }

    @Override
    public void preRollWALWriterRequest(ObserverContext<RegionServerCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "rollWALWriterRequest");
    }

    @Override
    public void preClearCompactionQueues(ObserverContext<RegionServerCoprocessorEnvironment> ctx) throws IOException
    {
        global(ctx, "clearCompactionQueues");
    }

    // Regions: reads

    @Override
    @SuppressWarnings("rawtypes")
    public void preGetOp(ObserverContext ctx, Get get, List result) throws IOException
    {
        Filter filter = readFilter(ctx, "get");
        if (filter != null) {
            get.setFilter(combine(filter, get.getFilter()));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void preScannerOpen(ObserverContext ctx, Scan scan) throws IOException
    {
        Filter filter = readFilter(ctx, "scan");
        if (filter != null) {
            scan.setFilter(combine(filter, scan.getFilter()));
        }
    }

    private static Filter combine(Filter ours, @Nullable Filter theirs)
    {
        return theirs == null ? ours : new FilterList(FilterList.Operator.MUST_PASS_ALL, ours, theirs);
    }

    /**
     * Decides a read of the region's table: refused when the caller may read nothing of it; unfiltered when the caller
     * may read all of it; otherwise filtered to the columns the caller may read.
     *
     * @return the filter, or {@code null} for none
     */
    @SuppressWarnings("rawtypes")
    private @Nullable Filter readFilter(ObserverContext ctx, String action) throws AccessDeniedException
    {
        Caller caller = caller(ctx);
        HBaseResource table = table(ctx);
        if (caller == null || SYSTEM_NAMESPACE.equals(table.namespace())) {
            return null;
        }
        Policies policies = runtime.policies();
        if (!policies.enforced()) {
            return null;
        }
        Policies.Verdict whole = policies.decide(caller, table, READ);
        if (whole.allowed() && !policies.namesColumns()) {
            report(caller, table, whole, true, action, null);
            return null;
        }
        if (whole.denied() || !whole.allowed() && !policies.uses(caller, table, READ)) {
            report(caller, table, whole, false, action, null);
            throw refused(caller, table, whole, READ);
        }
        report(caller, table, whole, true, action, whole.allowed() ? null : "only the columns the user may read");
        return new ColumnFilter((family, qualifier) -> policies.decide(caller, column(table, family, qualifier), READ).allowed());
    }

    /** A column of a table; HBase allows a column with an empty qualifier, which is decided at its family. */
    private static HBaseResource column(HBaseResource table, String family, String qualifier)
    {
        HBaseResource columnFamily = table.family(family);
        return qualifier.isEmpty() ? columnFamily : columnFamily.column(qualifier);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public CheckAndMutateResult preCheckAndMutate(ObserverContext ctx, CheckAndMutate checkAndMutate, CheckAndMutateResult result)
            throws IOException
    {
        HBaseResource table = table(ctx);
        HBaseResource checked = checkAndMutate.hasFilter() ? table
                : column(table, Bytes.toString(checkAndMutate.getFamily()), Bytes.toString(checkAndMutate.getQualifier()));
        require(ctx, "checkAndMutate", checked, READ);
        return result;
    }

    // Regions: writes

    @Override
    @SuppressWarnings("rawtypes")
    public void preBatchMutate(ObserverContext ctx, MiniBatchOperationInProgress miniBatchOp) throws IOException
    {
        Caller caller = caller(ctx);
        if (caller == null) {
            return;
        }
        Policies policies = runtime.policies();
        if (!policies.enforced()) {
            return;
        }
        HBaseResource table = table(ctx);
        Set<HBaseResource> written = new LinkedHashSet<>();
        Set<String> actions = new LinkedHashSet<>();
        for (int index = 0; index < miniBatchOp.size(); index++) {
            Mutation mutation = (Mutation) miniBatchOp.getOperation(index);
            actions.add(action(mutation));
            written.addAll(writes(table, mutation));
        }
        String action = actions.size() == 1 ? actions.iterator().next() : "batch";
        Policies.Verdict allowed = null;
        for (HBaseResource resource : written) {
            Policies.Verdict verdict = policies.decide(caller, resource, WRITE);
            if (!verdict.allowed()) {
                report(caller, resource, verdict, false, action, null);
                throw refused(caller, resource, verdict, WRITE);
            }
            allowed = verdict;
        }
        if (allowed != null) {
            report(caller, table, allowed, true, action, written.size() == 1 && written.contains(table) ? null
                    : written.size() + (written.size() == 1 ? " column" : " columns"));
        }
    }

    private static String action(Mutation mutation)
    {
        if (mutation instanceof Put) {
            return "put";
        }
        if (mutation instanceof Delete) {
            return "delete";
        }
        if (mutation instanceof Append) {
            return "append";
        }
        return mutation instanceof Increment ? "increment" : mutation.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Returns what a mutation writes: a column family for a family's deletion, which is also how HBase deletes a whole
     * row, otherwise each column it names; a mutation that names nothing writes the whole table.
     *
     * @param table the region's table
     * @param mutation the mutation
     * @return the resources
     */
    static Set<HBaseResource> writes(HBaseResource table, Mutation mutation)
    {
        Set<HBaseResource> written = new LinkedHashSet<>();
        if (mutation.getFamilyCellMap().isEmpty()) {
            written.add(table);
            return written;
        }
        for (Map.Entry<byte[], List<Cell>> family : mutation.getFamilyCellMap().entrySet()) {
            HBaseResource columnFamily = table.family(Bytes.toString(family.getKey()));
            for (Cell cell : family.getValue()) {
                if (cell.getType() == Cell.Type.DeleteFamily || cell.getType() == Cell.Type.DeleteFamilyVersion) {
                    written.add(columnFamily);
                }
                else {
                    written.add(column(table, Bytes.toString(family.getKey()), Bytes.toString(CellUtil.cloneQualifier(cell))));
                }
            }
            if (family.getValue().isEmpty()) {
                written.add(columnFamily);
            }
        }
        return written;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void preBulkLoadHFile(ObserverContext ctx, List familyPaths) throws IOException
    {
        require(ctx, "bulkLoadHFile", table(ctx), CREATE);
    }

    // Regions: administration

    @Override
    @SuppressWarnings("rawtypes")
    public void preFlush(ObserverContext ctx, FlushLifeCycleTracker tracker) throws IOException
    {
        require(ctx, "flush", table(ctx), ADMIN, CREATE);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void preCompactSelection(ObserverContext ctx, Store store, List candidates, CompactionLifeCycleTracker tracker) throws IOException
    {
        require(ctx, "compact", table(ctx), ADMIN, CREATE);
    }
}
