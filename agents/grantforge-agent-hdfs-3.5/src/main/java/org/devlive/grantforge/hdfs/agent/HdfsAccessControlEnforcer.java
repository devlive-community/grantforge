// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AuthorizationContext;
import org.apache.hadoop.ipc.CallerContext;
import org.apache.hadoop.security.AccessControlException;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizationContext;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.devlive.grantforge.hdfs.common.HdfsOperationAccess;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Native permission bridge for Hadoop 3.5; each supported callback retains Hadoop's native gate first. */
final class HdfsAccessControlEnforcer
        extends HdfsNativeEnforcer
{
    HdfsAccessControlEnforcer(@Nullable AccessControlEnforcer nativeEnforcer, Supplier<HdfsAuthorizer> authorizers)
    {
        super(nativeEnforcer, authorizers);
    }

    @Override
    public void checkPermissionWithContext(AuthorizationContext context) throws AccessControlException
    {
        try {
            nativeChecks().checkPermissionWithContext(context);
        }
        catch (AccessControlException refused) {
            nativeDenied(() -> convert(context), refused, false);
            throw refused;
        }
        apply(authorizer -> authorizer.authorize(convert(context)));
    }

    @Override
    public void checkSuperUserPermissionWithContext(AuthorizationContext context) throws AccessControlException
    {
        try {
            nativeChecks().checkSuperUserPermissionWithContext(context);
        }
        catch (AccessControlException refused) {
            nativeDenied(() -> convert(context), refused, true);
            throw refused;
        }
        apply(authorizer -> authorizer.authorizeSuperuser(convert(context),
                HdfsOperationAccess.superuserAccess(context.getOperationName())));
    }

    @Override
    public void denyUserAccess(AuthorizationContext context, String errorMessage) throws AccessControlException
    {
        // Hadoop has already rejected this access; the callback can never become a policy allow or native fallback.
        AccessControlException refused = new AccessControlException(errorMessage);
        nativeDenied(() -> convert(context), refused, true);
        throw refused;
    }

    private static HdfsAuthorizationContext convert(AuthorizationContext context)
    {
        INode[] inodes = context.getInodes();
        byte[][] components = context.getPathByNameArr();
        CallerContext caller = context.getCallerContext();
        return context(context.getCallerUgi(), inodes == null ? new INode[0] : inodes,
                components == null ? new byte[0][] : components, context.getSnapshotId(), context.getPath(),
                context.getAncestorIndex(), context.isDoCheckOwner(), context.getAncestorAccess(), context.getParentAccess(),
                context.getAccess(), context.getSubAccess(), context.isIgnoreEmptyDir())
                .operation(context.getOperationName()).callerContext(caller == null ? null : caller.getContext()).build();
    }
}
