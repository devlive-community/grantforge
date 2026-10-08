// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * The shared path-policy overlay. Version adapters must complete all native HDFS checks before invoking authorization;
 * a policy grant never replaces native traversal, ACL, owner, sticky-bit or subtree checks.
 */
public final class HdfsAuthorizer
{
    /** Native action bit for read. */
    public static final int READ = 4;
    /** Native action bit for write. */
    public static final int WRITE = 2;
    /** Native action bit for execute. */
    public static final int EXECUTE = 1;
    /** Native action mask requiring all three permissions. */
    public static final int ALL = 7;

    private static final Logger LOG = Logger.getLogger(HdfsAuthorizer.class.getName());
    private static final int MAX_SUBTREE_ENTRIES = 100_000;
    private final Supplier<Function<AccessRequest, AgentDecision>> sessions;
    private final Consumer<AccessEvent> record;
    private final BooleanSupplier nativeFallback;
    private final Supplier<HdfsMetrics> metrics;

    /**
     * Creates an overlay using lazily captured sessions and monitoring hooks from its runtime.
     *
     * @param sessions supplies one decision function per callback, bound to one immutable snapshot
     * @param record queues final audit events
     * @param nativeFallback whether undetermined decisions may retain native permission
     * @param metrics optional monitoring hooks, supplied as the no-op monitor when unavailable
     */
    public HdfsAuthorizer(Supplier<Function<AccessRequest, AgentDecision>> sessions, Consumer<AccessEvent> record,
            BooleanSupplier nativeFallback, Supplier<HdfsMetrics> metrics)
    {
        this.sessions = requireNonNull(sessions, "sessions");
        this.record = requireNonNull(record, "record");
        this.nativeFallback = requireNonNull(nativeFallback, "nativeFallback");
        this.metrics = requireNonNull(metrics, "metrics");
    }

    /**
     * Creates an overlay with a fixed policy session and no monitoring hooks.
     *
     * @param decide the snapshot's decision function
     * @param record queues final audit events
     * @param fallback whether undetermined decisions may retain native permission
     */
    public HdfsAuthorizer(Function<AccessRequest, AgentDecision> decide, Consumer<AccessEvent> record, boolean fallback)
    {
        this(() -> decide, record, () -> fallback, () -> HdfsMetrics.NONE);
    }

    /**
     * Checks the native callback's target, ancestors, parent and subtree against one captured policy snapshot.
     *
     * @param context the authenticated native callback, whose native permission checks have already succeeded
     * @throws IOException if a policy denies, strict mode has no decision, or the context or engine cannot be evaluated
     */
    public void authorize(HdfsAuthorizationContext context) throws IOException
    {
        monitor(HdfsMetrics::callback);
        try {
            evaluate(context, permissions(context));
        }
        catch (IOException denied) {
            throw denied;
        }
        catch (RuntimeException invalid) {
            monitor(HdfsMetrics::failure);
            event(context, new Permission(displayPath(context), "execute"), false, null, "invalid authorization context");
            throw new IOException("GrantForge could not check this HDFS authorization context: " + invalid.getMessage(), invalid);
        }
    }

    /**
     * Checks a superuser path using the adapter's inferred action mask after its native privilege gate has succeeded.
     * Pathless cluster administration retains native privilege checks and is audited without inventing a resource policy.
     *
     * @param context the authenticated superuser callback
     * @param action the inferred native action mask, from zero through seven
     * @throws IOException if path policies deny or their evaluation fails
     */
    public void authorizeSuperuser(HdfsAuthorizationContext context, int action) throws IOException
    {
        monitor(HdfsMetrics::superuserCallback);
        String path = context.path();
        if (path == null) {
            nativeAllowed(context);
            return;
        }
        if (action < 0 || action > ALL) {
            monitor(HdfsMetrics::failure);
            event(context, new Permission(path, "execute"), false, null, "invalid superuser action mask");
            throw new IOException("the superuser HDFS action mask must be between zero and seven");
        }
        Map<Permission, Permission> permissions = new LinkedHashMap<>();
        add(permissions, path, action == 0 ? EXECUTE : action);
        evaluate(context, new ArrayList<>(permissions.keySet()));
    }

    /**
     * Records an ordinary callback rejected by native HDFS; adapters must still throw their original native exception.
     *
     * @param context the rejected callback
     * @param reason the native rejection reason
     */
    public void nativeDenied(HdfsAuthorizationContext context, String reason)
    {
        nativeDenied(context, reason, false);
    }

    /**
     * Records a native rejection without consulting policies; explicit native denials remain authoritative.
     *
     * @param context the rejected callback
     * @param reason the native rejection reason
     * @param superuser whether this was the native superuser privilege callback
     */
    public void nativeDenied(HdfsAuthorizationContext context, String reason, boolean superuser)
    {
        monitor(superuser ? HdfsMetrics::superuserCallback : HdfsMetrics::callback);
        monitor(HdfsMetrics::nativeDeny);
        nativeEvent(context, false, reason);
    }

    /**
     * Audits a successful native-only check, such as pathless cluster administration.
     *
     * @param context the native callback
     */
    public void nativeAllowed(HdfsAuthorizationContext context)
    {
        nativeEvent(context, true, null);
    }

    // Each queued inode needs its own path holder. Discovery is capped before child wrappers or permissions are allocated.
    private static List<Permission> permissions(HdfsAuthorizationContext context)
    {
        Map<Permission, Permission> permissions = new LinkedHashMap<>();
        int last = context.nodeCount() - 1;
        if (last < 0) {
            throw new IllegalArgumentException("the authorization context has no inode path");
        }
        int ancestor = context.ancestorIndex();
        while (ancestor >= 0 && context.node(ancestor) == null) {
            ancestor--;
        }
        for (int index = 0; index <= ancestor; index++) {
            add(permissions, path(context, index), EXECUTE);
        }
        if (last > 0 && ancestor >= 0) {
            add(permissions, path(context, ancestor), context.ancestorAccess());
        }
        if (last > 0 && context.node(last - 1) != null) {
            add(permissions, path(context, last - 1), context.parentAccess());
        }
        String target = path(context, last);
        add(permissions, target, context.access());
        // Parent/ancestor mutations also restrict the named target, including targets not yet present in the inode tree.
        add(permissions, target, context.ancestorAccess());
        add(permissions, target, context.parentAccess());
        if (context.checkOwner()) {
            add(permissions, target, WRITE);
        }
        HdfsNode node = context.node(last);
        if (context.subAccess() != 0 && node != null && node.isDirectory()) {
            Deque<Subtree> pending = new ArrayDeque<>();
            pending.push(new Subtree(node, target));
            int entries = 1;
            while (!pending.isEmpty()) {
                Subtree subtree = pending.pop();
                // Empty directories and files remain protected: deleting an ancestor cannot bypass a descendant policy deny.
                add(permissions, subtree.path, context.subAccess());
                if (subtree.node.isDirectory()) {
                    List<HdfsNode> children = subtree.node.children(context.snapshotId());
                    if (children.size() > MAX_SUBTREE_ENTRIES - entries) {
                        throw new IllegalArgumentException("subtree exceeds " + MAX_SUBTREE_ENTRIES
                                + " entries; authorize smaller subtrees separately");
                    }
                    entries += children.size();
                    for (HdfsNode child : children) {
                        pending.push(new Subtree(child, childPath(subtree.path, child.name())));
                    }
                }
            }
        }
        if (permissions.isEmpty()) {
            add(permissions, target, EXECUTE);
        }
        return new ArrayList<>(permissions.keySet());
    }

    private static String path(HdfsAuthorizationContext context, int nodeIndex)
    {
        return context.inodePath(nodeIndex);
    }

    private static String childPath(String parent, String name)
    {
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    private static void add(Map<Permission, Permission> permissions, String path, int action)
    {
        if ((action & READ) != 0) {
            Permission permission = new Permission(path, "read");
            permissions.put(permission, permission);
        }
        if ((action & WRITE) != 0) {
            Permission permission = new Permission(path, "write");
            permissions.put(permission, permission);
        }
        if ((action & EXECUTE) != 0) {
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
        StringBuilder live = new StringBuilder();
        boolean skipName = false;
        for (String element : path.split("/", -1)) {
            if (skipName) {
                skipName = false;
            }
            else if (".snapshot".equals(element)) {
                skipName = true;
            }
            else if (!element.isEmpty()) {
                live.append('/').append(element);
            }
        }
        return live.length() == 0 ? "/" : live.toString();
    }

    private void evaluate(HdfsAuthorizationContext context, List<Permission> permissions) throws IOException
    {
        String[] groups = context.groups();
        Instant time = Instant.now();
        Map<String, Object> attributes = new LinkedHashMap<>();
        String clientIp = context.clientIp();
        if (clientIp != null) {
            attributes.put("clientAddress", clientIp);
        }
        String operation = context.operation();
        if (operation != null) {
            attributes.put("operation", operation);
        }
        String caller = context.callerContext();
        if (caller != null) {
            attributes.put("callerContext", caller);
        }
        Function<AccessRequest, AgentDecision> decide;
        try {
            decide = requireNonNull(sessions.get(), "policy session");
        }
        catch (RuntimeException broken) {
            monitor(HdfsMetrics::failure);
            event(context, new Permission(displayPath(context), "execute"), false, null, "policy evaluation failed");
            throw new IOException("GrantForge could not evaluate HDFS access", broken);
        }
        boolean fallback = nativeFallback.getAsBoolean();
        List<AgentDecision> decisions = new ArrayList<>();
        for (Permission permission : permissions) {
            AgentDecision decision;
            try {
                decision = requireNonNull(decide.apply(AccessRequest.builder(context.user(), permission.access)
                        .resource("path", permission.path).groups(groups).time(time).context(attributes).build()), "policy decision");
            }
            catch (RuntimeException broken) {
                monitor(HdfsMetrics::failure);
                event(context, permission, false, null, "policy evaluation failed");
                throw new IOException("GrantForge could not evaluate HDFS access", broken);
            }
            monitor(monitor -> monitor.decision(decision));
            boolean allowed = decision.allowed() || (decision.outcome() == AgentDecision.Outcome.NOT_DETERMINED && fallback);
            if (!allowed) {
                event(context, permission, false, decision, "GrantForge denied HDFS access");
                throw new IOException("GrantForge denied " + permission.access + " for user " + context.user() + " on "
                        + permission.path + " (" + decision.outcome() + ")");
            }
            decisions.add(decision);
        }
        for (int index = 0; index < permissions.size(); index++) {
            event(context, permissions.get(index), true, decisions.get(index), null);
        }
    }

    private static String displayPath(HdfsAuthorizationContext context)
    {
        String requested = context.path();
        if (requested != null) {
            return requested;
        }
        return context.nodeCount() == 0 ? "/" : path(context, context.nodeCount() - 1);
    }

    private void nativeEvent(HdfsAuthorizationContext context, boolean allowed, @Nullable String reason)
    {
        event(context, new Permission(displayPath(context), "native"), allowed, null, reason);
    }

    private void event(HdfsAuthorizationContext context, Permission permission, boolean allowed, @Nullable AgentDecision decision,
            @Nullable String reason)
    {
        String resource = permission.path;
        String details = reason;
        if (resource.length() > 1000) {
            details = "resourceLength=" + resource.length() + "; resourceSha256=" + digest(resource)
                    + (reason == null ? "" : "; " + reason);
            resource = resource.substring(0, 985) + "...[truncated]";
        }
        AccessEvent.Builder event = AccessEvent.builder(context.user(), resource, permission.access, allowed)
                .clientIp(context.clientIp()).resourceType("path").action(context.operation());
        if (decision != null) {
            event.decidedBy(decision);
        }
        if (!allowed && !"native".equals(permission.access)) {
            event.enforcedByGrantForge();
        }
        event.request(details == null ? context.callerContext() : details);
        try {
            record.accept(event.build());
        }
        catch (RuntimeException unavailable) {
            LOG.log(Level.WARNING, "Could not queue an HDFS access audit event", unavailable);
        }
    }

    private void monitor(Consumer<HdfsMetrics> update)
    {
        try {
            update.accept(requireNonNull(metrics.get(), "metrics"));
        }
        catch (RuntimeException unavailable) {
            LOG.log(Level.FINE, "HDFS agent monitoring is unavailable", unavailable);
        }
    }

    private static String digest(String resource)
    {
        byte[] bytes;
        try {
            bytes = MessageDigest.getInstance("SHA-256").digest(resource.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        char[] hex = new char[bytes.length * 2];
        String alphabet = "0123456789abcdef";
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            hex[index * 2] = alphabet.charAt(value >>> 4);
            hex[index * 2 + 1] = alphabet.charAt(value & 0xf);
        }
        return new String(hex);
    }

    private static final class Permission
    {
        final String path;
        final String access;

        Permission(String path, String access)
        {
            this.path = path;
            this.access = access;
        }

        @Override
        public boolean equals(@Nullable Object other)
        {
            if (!(other instanceof Permission)) {
                return false;
            }
            Permission permission = (Permission) other;
            return path.equals(permission.path) && access.equals(permission.access);
        }

        @Override
        public int hashCode()
        {
            return 31 * path.hashCode() + access.hashCode();
        }
    }

    private static final class Subtree
    {
        final HdfsNode node;
        final String path;

        Subtree(HdfsNode node, String path)
        {
            this.node = node;
            this.path = path;
        }
    }
}
