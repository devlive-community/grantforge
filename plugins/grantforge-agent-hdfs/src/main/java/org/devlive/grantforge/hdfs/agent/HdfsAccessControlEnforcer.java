// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AuthorizationContext;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.hdfs.util.ReadOnlyList;
import org.apache.hadoop.ipc.CallerContext;
import org.apache.hadoop.ipc.Server;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Adds path policy checks only after Hadoop has checked traversal, ACLs, ownership, sticky bits and subtrees. */
final class HdfsAccessControlEnforcer
        implements AccessControlEnforcer
{
    private static final Logger LOG = Logger.getLogger(HdfsAccessControlEnforcer.class.getName());
    private static final int MAX_SUBTREE_ENTRIES = 100_000;
    private final @Nullable AccessControlEnforcer nativeEnforcer;
    private final Supplier<Function<AccessRequest, AgentDecision>> decisionSession;
    private final Consumer<AccessEvent> record;
    private final BooleanSupplier nativeFallback;

    HdfsAccessControlEnforcer(@Nullable AccessControlEnforcer nativeEnforcer, Function<AccessRequest, AgentDecision> decide,
            Consumer<AccessEvent> record, BooleanSupplier nativeFallback)
    {
        this(nativeEnforcer, () -> decide, record, nativeFallback);
    }

    HdfsAccessControlEnforcer(@Nullable AccessControlEnforcer nativeEnforcer,
            Supplier<Function<AccessRequest, AgentDecision>> decisionSession, Consumer<AccessEvent> record, BooleanSupplier nativeFallback)
    {
        this.nativeEnforcer = nativeEnforcer;
        this.decisionSession = decisionSession;
        this.record = record;
        this.nativeFallback = nativeFallback;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void checkPermission(String fsOwner, String supergroup, UserGroupInformation callerUgi, INodeAttributes[] inodeAttrs,
            INode[] inodes, byte[][] pathByNameArr, int snapshotId, @Nullable String path, int ancestorIndex,
            boolean doCheckOwner, @Nullable FsAction ancestorAccess, @Nullable FsAction parentAccess, @Nullable FsAction access,
            @Nullable FsAction subAccess, boolean ignoreEmptyDir) throws AccessControlException
    {
        AuthorizationContext context = new AuthorizationContext.Builder().fsOwner(fsOwner).supergroup(supergroup)
                .callerUgi(callerUgi).inodeAttrs(inodeAttrs).inodes(inodes).pathByNameArr(pathByNameArr).snapshotId(snapshotId)
                .path(path).ancestorIndex(ancestorIndex).doCheckOwner(doCheckOwner).ancestorAccess(ancestorAccess)
                .parentAccess(parentAccess).access(access).subAccess(subAccess).ignoreEmptyDir(ignoreEmptyDir).build();
        AccessControlEnforcer nativeChecks = nativeChecks();
        try {
            nativeChecks.checkPermission(fsOwner, supergroup, callerUgi, inodeAttrs, inodes, pathByNameArr, snapshotId, path,
                    ancestorIndex, doCheckOwner, ancestorAccess, parentAccess, access, subAccess, ignoreEmptyDir);
        }
        catch (AccessControlException denied) {
            nativeEvent(context, false, denied.getMessage());
            throw denied;
        }
        enforce(context);
    }

    @Override
    public void checkPermissionWithContext(AuthorizationContext context) throws AccessControlException
    {
        try {
            nativeChecks().checkPermissionWithContext(context);
        }
        catch (AccessControlException denied) {
            nativeEvent(context, false, denied.getMessage());
            throw denied;
        }
        enforce(context);
    }

    @Override
    public void checkSuperUserPermissionWithContext(AuthorizationContext context) throws AccessControlException
    {
        try {
            nativeChecks().checkSuperUserPermissionWithContext(context);
        }
        catch (AccessControlException denied) {
            nativeEvent(context, false, denied.getMessage());
            throw denied;
        }
        String path = context.getPath();
        if (path == null) {
            // Pathless cluster administration has no resource in the HDFS service model. Hadoop's privilege gate applies.
            nativeEvent(context, true, null);
            return;
        }
        Map<Permission, Permission> permissions = new LinkedHashMap<>();
        add(permissions, path, HdfsOperationAccess.superuserAccess(context.getOperationName()));
        evaluate(context, permissions.keySet().stream().toList());
    }

    @Override
    public void denyUserAccess(AuthorizationContext context, String errorMessage) throws AccessControlException
    {
        nativeEvent(context, false, errorMessage);
        // Never depend on the delegate to throw: this callback means Hadoop already rejected the request.
        throw new AccessControlException(errorMessage);
    }

    private AccessControlEnforcer nativeChecks() throws AccessControlException
    {
        AccessControlEnforcer checks = nativeEnforcer;
        if (checks == null) {
            throw new AccessControlException("Hadoop did not supply its native permission checker");
        }
        return checks;
    }

    private void enforce(AuthorizationContext context) throws AccessControlException
    {
        try {
            evaluate(context, permissions(context));
        }
        catch (AccessControlException denied) {
            throw denied;
        }
        catch (RuntimeException invalid) {
            String path = context.getPath();
            event(context, new Permission(path == null ? "/" : path, "execute"), false, null, "invalid authorization context");
            throw failure("GrantForge could not check this HDFS authorization context: " + invalid.getMessage(), invalid);
        }
    }

    // Each queued inode needs its own path holder; the discovery cap bounds these allocations.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static List<Permission> permissions(AuthorizationContext context)
    {
        Map<Permission, Permission> permissions = new LinkedHashMap<>();
        INode[] inodes = context.getInodes();
        int last = inodes.length - 1;
        if (last < 0) {
            throw new IllegalArgumentException("the authorization context has no inode path");
        }
        int ancestor = context.getAncestorIndex();
        while (ancestor >= 0 && inodes[ancestor] == null) {
            ancestor--;
        }
        for (int index = 0; index <= ancestor; index++) {
            add(permissions, path(context, index), FsAction.EXECUTE);
        }
        if (last > 0 && ancestor >= 0) {
            add(permissions, path(context, ancestor), context.getAncestorAccess());
        }
        if (last > 0 && inodes[last - 1] != null) {
            add(permissions, path(context, last - 1), context.getParentAccess());
        }
        String target = path(context, last);
        add(permissions, target, context.getAccess());
        // Creation, removal and rename also restrict the named target, including targets not yet in the inode tree.
        add(permissions, target, context.getAncestorAccess());
        add(permissions, target, context.getParentAccess());
        if (context.isDoCheckOwner()) {
            add(permissions, target, FsAction.WRITE);
        }
        FsAction subAccess = context.getSubAccess();
        INode inode = inodes[last];
        if (subAccess != null && inode != null && inode.isDirectory()) {
            Deque<Subtree> pending = new ArrayDeque<>();
            pending.push(new Subtree(inode, target));
            int entries = 1;
            while (!pending.isEmpty()) {
                Subtree subtree = pending.pop();
                // Apply the overlay to empty directories and child files too, so deleting an ancestor cannot bypass a deny.
                add(permissions, subtree.path, subAccess);
                if (subtree.inode.isDirectory()) {
                    ReadOnlyList<INode> children = subtree.inode.asDirectory().getChildrenList(context.getSnapshotId());
                    if (children.size() > MAX_SUBTREE_ENTRIES - entries) {
                        throw new IllegalArgumentException("subtree exceeds " + MAX_SUBTREE_ENTRIES
                                + " entries; authorize smaller subtrees separately");
                    }
                    entries += children.size();
                    for (INode child : children) {
                        String name = new String(child.getLocalNameBytes(), StandardCharsets.UTF_8);
                        pending.push(new Subtree(child, childPath(subtree.path, name)));
                    }
                }
            }
        }
        if (permissions.isEmpty()) {
            // Root/NONE checks have no traversed ancestors. Still require a policy decision rather than allowing vacuously.
            add(permissions, target, FsAction.EXECUTE);
        }
        return List.copyOf(permissions.keySet());
    }

    // Each UTF-8 component must be decoded separately before reconstructing the requested path.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static String path(AuthorizationContext context, int inodeIndex)
    {
        if (inodeIndex == context.getInodes().length - 1 && context.getPath() != null) {
            return context.getPath();
        }
        byte[][] components = context.getPathByNameArr();
        // Hadoop also sends a single inode with the full path's components (content-summary checks).
        int componentIndex = inodeIndex + components.length - context.getInodes().length;
        StringBuilder path = new StringBuilder();
        for (int index = 0; index <= componentIndex; index++) {
            byte[] component = components[index];
            if (component != null && component.length > 0) {
                path.append('/').append(new String(component, StandardCharsets.UTF_8));
            }
        }
        return path.length() == 0 ? "/" : path.toString();
    }

    private static String childPath(String parent, String name)
    {
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    private static void add(Map<Permission, Permission> permissions, String path, @Nullable FsAction action)
    {
        if (action == null || action == FsAction.NONE) {
            return;
        }
        if (action.implies(FsAction.READ)) {
            Permission permission = new Permission(path, "read");
            permissions.put(permission, permission);
        }
        if (action.implies(FsAction.WRITE)) {
            Permission permission = new Permission(path, "write");
            permissions.put(permission, permission);
        }
        if (action.implies(FsAction.EXECUTE)) {
            Permission permission = new Permission(path, "execute");
            permissions.put(permission, permission);
        }
        String live = livePath(path);
        if (!path.equals(live)) {
            add(permissions, live, action);
        }
    }

    private static String livePath(String path)
    {
        if (!path.contains("/.snapshot")) {
            return path;
        }
        String[] elements = path.split("/", -1);
        StringBuilder live = new StringBuilder();
        boolean skipSnapshotName = false;
        for (String element : elements) {
            if (skipSnapshotName) {
                skipSnapshotName = false;
                continue;
            }
            if (".snapshot".equals(element)) {
                skipSnapshotName = true;
            }
            else if (!element.isEmpty()) {
                live.append('/').append(element);
            }
        }
        return live.length() == 0 ? "/" : live.toString();
    }

    private void evaluate(AuthorizationContext context, List<Permission> permissions) throws AccessControlException
    {
        String user = context.getCallerUgi().getShortUserName();
        String[] groups = context.getCallerUgi().getGroupNames();
        Instant time = Instant.now();
        Map<String, Object> attributes = new LinkedHashMap<>();
        String clientIp = clientIp();
        if (clientIp != null) {
            attributes.put("clientAddress", clientIp);
        }
        String operation = context.getOperationName();
        if (operation != null) {
            attributes.put("operation", operation);
        }
        List<AgentDecision> decisions = new ArrayList<>();
        Function<AccessRequest, AgentDecision> decide;
        try {
            // Traversal, target and subtree checks must use one policy version: combining grants from different
            // refreshes could allow an operation that no individual snapshot permits.
            decide = decisionSession.get();
        }
        catch (RuntimeException broken) {
            String path = context.getPath();
            event(context, new Permission(path == null ? "/" : path, "execute"), false, null, "policy evaluation failed");
            throw failure("GrantForge could not evaluate HDFS access", broken);
        }
        for (Permission permission : permissions) {
            AgentDecision decision;
            try {
                decision = decide.apply(AccessRequest.builder(user, permission.access).resource("path", permission.path)
                        .groups(groups).time(time).context(attributes).build());
            }
            catch (RuntimeException broken) {
                event(context, permission, false, null, "policy evaluation failed");
                throw failure("GrantForge could not evaluate HDFS access", broken);
            }
            boolean allowed = decision.allowed()
                    || (decision.outcome() == AgentDecision.Outcome.NOT_DETERMINED && nativeFallback.getAsBoolean());
            if (!allowed) {
                event(context, permission, false, decision, "GrantForge denied HDFS access");
                throw new AccessControlException("GrantForge denied " + permission.access + " for user " + user
                        + " on " + permission.path + " (" + decision.outcome() + ")");
            }
            decisions.add(decision);
        }
        // Emit successful checks only after the entire operation's overlay has passed.
        for (int index = 0; index < permissions.size(); index++) {
            event(context, permissions.get(index), true, decisions.get(index), null);
        }
    }

    private void nativeEvent(AuthorizationContext context, boolean allowed, @Nullable String reason)
    {
        String path = context.getPath();
        if (path == null && context.getInodes() != null && context.getInodes().length > 0 && context.getPathByNameArr() != null) {
            path = path(context, context.getInodes().length - 1);
        }
        event(context, new Permission(path == null ? "/" : path, "native"), allowed, null, reason);
    }

    private void event(AuthorizationContext context, Permission permission, boolean allowed, @Nullable AgentDecision decision,
            @Nullable String reason)
    {
        String resource = permission.path;
        String details = reason;
        if (resource.length() > 1000) {
            details = "resourceLength=" + resource.length() + "; resourceSha256=" + digest(resource)
                    + (reason == null ? "" : "; " + reason);
            resource = resource.substring(0, 985) + "...[truncated]";
        }
        AccessEvent.Builder event = AccessEvent.builder(context.getCallerUgi().getShortUserName(), resource,
                        permission.access, allowed)
                .clientIp(clientIp()).resourceType("path").action(context.getOperationName());
        if (decision != null) {
            event.decidedBy(decision);
        }
        if (!allowed && !"native".equals(permission.access)) {
            event.enforcedByGrantForge();
        }
        CallerContext caller = context.getCallerContext();
        event.request(details == null ? (caller == null ? null : caller.getContext()) : details);
        try {
            record.accept(event.build());
        }
        catch (RuntimeException unavailable) {
            LOG.log(Level.WARNING, "Could not queue an HDFS access audit event", unavailable);
        }
    }

    private static @Nullable String clientIp()
    {
        InetAddress remote = Server.getRemoteIp();
        return remote == null ? null : remote.getHostAddress();
    }

    private static AccessControlException failure(String message, RuntimeException cause)
    {
        AccessControlException denied = new AccessControlException(message);
        denied.initCause(cause);
        return denied;
    }

    private static String digest(String resource)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Permission(String path, String access) {}

    private record Subtree(INode inode, String path) {}
}
