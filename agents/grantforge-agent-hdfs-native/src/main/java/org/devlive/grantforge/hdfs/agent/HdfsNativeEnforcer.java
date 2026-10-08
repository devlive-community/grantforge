// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.ipc.Server;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizationContext;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.devlive.grantforge.hdfs.common.HdfsNode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Shared native bridge compiled separately against each supported Hadoop version's baseline permission SPI. */
abstract class HdfsNativeEnforcer
        implements AccessControlEnforcer
{
    private static final Logger LOG = Logger.getLogger(HdfsNativeEnforcer.class.getName());
    private final @Nullable AccessControlEnforcer nativeEnforcer;
    private final Supplier<HdfsAuthorizer> authorizers;

    HdfsNativeEnforcer(@Nullable AccessControlEnforcer nativeEnforcer, Supplier<HdfsAuthorizer> authorizers)
    {
        this.nativeEnforcer = nativeEnforcer;
        this.authorizers = authorizers;
    }

    @Override
    @SuppressWarnings("deprecation")
    public final void checkPermission(String fsOwner, String supergroup, UserGroupInformation user, INodeAttributes[] inodeAttrs,
            INode[] inodes, byte[][] components, int snapshotId, @Nullable String path, int ancestorIndex, boolean checkOwner,
            @Nullable FsAction ancestorAccess, @Nullable FsAction parentAccess, @Nullable FsAction access,
            @Nullable FsAction subAccess, boolean ignoreEmptyDir) throws AccessControlException
    {
        try {
            nativeChecks().checkPermission(fsOwner, supergroup, user, inodeAttrs, inodes, components, snapshotId, path,
                    ancestorIndex, checkOwner, ancestorAccess, parentAccess, access, subAccess, ignoreEmptyDir);
        }
        catch (AccessControlException refused) {
            nativeDenied(() -> context(user, inodes, components, snapshotId, path, ancestorIndex, checkOwner, ancestorAccess,
                    parentAccess, access, subAccess, ignoreEmptyDir).build(), refused, false);
            throw refused;
        }
        apply(authorizer -> authorizer.authorize(context(user, inodes, components, snapshotId, path, ancestorIndex, checkOwner,
                ancestorAccess, parentAccess, access, subAccess, ignoreEmptyDir).build()));
    }

    protected final AccessControlEnforcer nativeChecks() throws AccessControlException
    {
        AccessControlEnforcer checker = nativeEnforcer;
        if (checker == null) {
            throw new AccessControlException("Hadoop did not supply its native permission checker");
        }
        return checker;
    }

    protected final void apply(AuthorizationOperation operation) throws AccessControlException
    {
        try {
            operation.apply(authorizers.get());
        }
        catch (IOException | RuntimeException failure) {
            String message = failure.getMessage();
            AccessControlException refused = new AccessControlException(message == null ? "GrantForge could not authorize HDFS access" : message);
            refused.initCause(failure);
            throw refused;
        }
    }

    protected final void nativeDenied(Supplier<HdfsAuthorizationContext> context, AccessControlException refused, boolean superuser)
    {
        try {
            String message = refused.getMessage();
            authorizers.get().nativeDenied(context.get(), message == null ? "Hadoop denied HDFS access" : message, superuser);
        }
        catch (RuntimeException unavailable) {
            // Recording a native rejection must never replace it with an audit or lifecycle failure.
            LOG.log(Level.FINE, "Could not report a native HDFS permission rejection", unavailable);
        }
    }

    // A null inode is an expected missing path component during creation; wrap only existing native inodes.
    protected static HdfsAuthorizationContext.Builder context(UserGroupInformation user, @Nullable INode[] inodes,
            byte[] @Nullable [] components, int snapshotId, @Nullable String path, int ancestorIndex, boolean checkOwner,
            @Nullable FsAction ancestorAccess, @Nullable FsAction parentAccess, @Nullable FsAction access,
            @Nullable FsAction subAccess, boolean ignoreEmptyDir)
    {
        @Nullable HdfsNode[] nodes = new HdfsNode[inodes.length];
        for (int index = 0; index < inodes.length; index++) {
            INode inode = inodes[index];
            if (inode != null) {
                nodes[index] = new HdfsNativeNode(inode);
            }
        }
        InetAddress remote = Server.getRemoteIp();
        return HdfsAuthorizationContext.builder(user.getShortUserName(), user.getGroupNames()).nodes(nodes).components(components)
                .snapshotId(snapshotId).path(path).ancestorIndex(ancestorIndex).checkOwner(checkOwner).ignoreEmptyDir(ignoreEmptyDir)
                .actions(mask(ancestorAccess), mask(parentAccess), mask(access), mask(subAccess))
                .clientIp(remote == null ? null : remote.getHostAddress());
    }

    private static int mask(@Nullable FsAction action)
    {
        if (action == null) {
            return 0;
        }
        // Use the declared permissions, without relying on enum ordinal ordering across Hadoop versions.
        return (action.implies(FsAction.READ) ? 4 : 0) | (action.implies(FsAction.WRITE) ? 2 : 0)
                | (action.implies(FsAction.EXECUTE) ? 1 : 0);
    }

    @FunctionalInterface
    protected interface AuthorizationOperation
    {
        void apply(HdfsAuthorizer authorizer) throws IOException;
    }
}
