// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.fs.permission.AclEntry;
import org.apache.hadoop.fs.permission.AclEntryScope;
import org.apache.hadoop.fs.permission.AclEntryType;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hdfs.server.namenode.AclEntryStatusFormat;
import org.apache.hadoop.hdfs.server.namenode.AclFeature;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.hdfs.server.namenode.INodeDirectory;
import org.apache.hadoop.hdfs.server.namenode.INodeDirectoryAttributes;
import org.apache.hadoop.hdfs.util.ReadOnlyList;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizationContext;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsNativeEnforcerTest
{
    @Test
    @SuppressWarnings("deprecation")
    void forwardsEveryNativeArgumentAndPreservesMissingInodesAndActionMasks() throws AccessControlException, IOException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        HdfsNativeEnforcer enforcer = new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer);
        UserGroupInformation user = UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"});
        INode[] inodes = {mock(INode.class), null};
        INodeAttributes[] attributes = {mock(INodeAttributes.class), null};
        byte[][] components = {null, "created".getBytes(StandardCharsets.UTF_8)};

        enforcer.checkPermission("hdfs", "supergroup", user, attributes, inodes, components, 19, "/created", 0, true,
                FsAction.WRITE_EXECUTE, FsAction.WRITE, null, FsAction.ALL, true);

        verify(nativeChecks).checkPermission("hdfs", "supergroup", user, attributes, inodes, components, 19, "/created", 0, true,
                FsAction.WRITE_EXECUTE, FsAction.WRITE, null, FsAction.ALL, true);
        ArgumentCaptor<HdfsAuthorizationContext> context = ArgumentCaptor.forClass(HdfsAuthorizationContext.class);
        verify(authorizer).authorize(context.capture());
        assertThat(context.getValue().user()).isEqualTo("alice");
        assertThat(context.getValue().groups()).containsExactly("analysts");
        assertThat(context.getValue().nodes()).hasSize(2);
        assertThat(context.getValue().nodes()[1]).isNull();
        assertThat(context.getValue().components()[0]).isNull();
        assertThat(context.getValue().ancestorAccess()).isEqualTo(3);
        assertThat(context.getValue().parentAccess()).isEqualTo(2);
        assertThat(context.getValue().access()).isZero();
        assertThat(context.getValue().subAccess()).isEqualTo(7);
        assertThat(context.getValue().snapshotId()).isEqualTo(19);
        assertThat(context.getValue().checkOwner()).isTrue();
        assertThat(context.getValue().ignoreEmptyDir()).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    void neverOverridesANativeDenialAndPreservesItWhenAuditingFails() throws IOException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        UserGroupInformation user = UserGroupInformation.createUserForTesting("alice", new String[0]);
        INode[] inodes = {mock(INode.class)};
        INodeAttributes[] attributes = {mock(INodeAttributes.class)};
        byte[][] components = {null};
        AccessControlException refused = new AccessControlException("native refused");
        doThrow(refused).when(nativeChecks).checkPermission("hdfs", "supergroup", user, attributes, inodes, components,
                19, "/", -1, false, null, null, FsAction.READ, null, false);
        doThrow(new IllegalStateException("audit failed")).when(authorizer).nativeDenied(any(), eq("native refused"), eq(false));

        assertThatThrownBy(() -> new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer)
                .checkPermission("hdfs", "supergroup", user, attributes, inodes, components, 19, "/", -1, false,
                        null, null, FsAction.READ, null, false)).isSameAs(refused);
        verify(authorizer, never()).authorize(any());
    }

    @Test
    @SuppressWarnings("deprecation")
    void translatesNeutralPolicyErrorsIntoHadoopsAccessControlException() throws IOException
    {
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        IOException denied = new IOException("GrantForge denied read");
        doThrow(denied).when(authorizer).authorize(any());
        HdfsNativeEnforcer enforcer = new HdfsAccessControlEnforcer(mock(AccessControlEnforcer.class), () -> authorizer);
        assertThatThrownBy(() -> enforcer.checkPermission("hdfs", "supergroup",
                UserGroupInformation.createUserForTesting("alice", new String[0]), new INodeAttributes[0], new INode[0],
                new byte[0][], 19, "/", -1, false, null, null, FsAction.READ, null, false))
                .isInstanceOf(AccessControlException.class).hasMessage("GrantForge denied read").hasCause(denied);
        assertThatThrownBy(() -> new HdfsAccessControlEnforcer(null, () -> authorizer).checkPermission("hdfs", "supergroup",
                UserGroupInformation.createUserForTesting("alice", new String[0]), new INodeAttributes[0], new INode[0],
                new byte[0][], 19, "/", -1, false, null, null, FsAction.READ, null, false))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("native permission checker");
    }

    @Test
    @SuppressWarnings("deprecation")
    void preservesTheRealNameNodeModeDenialBeforeAnyPolicyGrant() throws IOException, ReflectiveOperationException
    {
        UserGroupInformation user = UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"});
        AccessControlEnforcer nativeChecks = nativeChecker(user);
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        INodeAttributes attributes = mock(INodeAttributes.class);
        when(attributes.getUserName()).thenReturn("bob");
        when(attributes.getGroupName()).thenReturn("analysts");
        when(attributes.getFsPermission()).thenReturn(new FsPermission((short) 0000));
        when(attributes.getLocalNameBytes()).thenReturn(new byte[0]);

        assertThatThrownBy(() -> new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer).checkPermission("hdfs", "supergroup", user,
                new INodeAttributes[] {attributes}, new INode[] {mock(INode.class)}, new byte[][] {null}, 19, "/", -1, false,
                null, null, FsAction.READ, null, false)).isInstanceOf(AccessControlException.class);
        verify(authorizer, never()).authorize(any());
    }

    @Test
    void malformedLegacyArraysFailClosedAfterTheNativeGate() throws ReflectiveOperationException
    {
        HdfsAccessControlEnforcer enforcer = new HdfsAccessControlEnforcer(mock(AccessControlEnforcer.class), () -> mock(HdfsAuthorizer.class));
        Method callback = HdfsAccessControlEnforcer.class.getMethod("checkPermission", String.class, String.class, UserGroupInformation.class,
                INodeAttributes[].class, INode[].class, byte[][].class, int.class, String.class, int.class, boolean.class,
                FsAction.class, FsAction.class, FsAction.class, FsAction.class, boolean.class);
        assertThatThrownBy(() -> callback.invoke(enforcer, "hdfs", "supergroup",
                UserGroupInformation.createUserForTesting("alice", new String[0]), new INodeAttributes[0], null, new byte[0][],
                19, "/", -1, false, null, null, FsAction.READ, null, false))
                .isInstanceOf(InvocationTargetException.class).cause().isInstanceOf(AccessControlException.class)
                .hasCauseInstanceOf(NullPointerException.class);
    }

    @Test
    void nativeTraversalOwnerStickyBitAndSubtreeDenialsCannotBeOverriddenByAPolicyGrant() throws IOException, ReflectiveOperationException
    {
        NativeFixture traversal = new NativeFixture();
        traversal.attributes[0] = directoryAttributes("", "bob", (short) 0000);
        assertNativeDenied(traversal);

        NativeFixture owner = new NativeFixture();
        owner.checkOwner = true;
        assertNativeDenied(owner);

        NativeFixture sticky = new NativeFixture();
        sticky.targetAccess = null;
        sticky.parentAccess = FsAction.WRITE;
        sticky.attributes[1] = directoryAttributes("data", "bob", (short) 01777);
        assertNativeDenied(sticky);

        NativeFixture subtree = new NativeFixture();
        subtree.targetAccess = null;
        subtree.subAccess = FsAction.ALL;
        INodeDirectory restricted = directory("restricted");
        INodeDirectoryAttributes restrictedAttributes = directoryAttributes("restricted", "bob", (short) 0000);
        when(restricted.getSnapshotINode(19)).thenReturn(restrictedAttributes);
        subtree.inodes[2] = restricted;
        subtree.attributes[2] = restrictedAttributes;
        subtree.components[2] = "restricted".getBytes(StandardCharsets.UTF_8);
        subtree.path = "/data/restricted";
        assertNativeDenied(subtree);
    }

    @Test
    void realNativeReadSucceedsButNativeAclDenialStillOverridesAPolicyGrant() throws IOException, ReflectiveOperationException
    {
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        NativeFixture allowed = new NativeFixture();
        allowed.check(new HdfsAccessControlEnforcer(nativeChecker(allowed.user), () -> authorizer));
        verify(authorizer).authorize(any());

        NativeFixture aclDenied = new NativeFixture();
        AclEntry alice = new AclEntry.Builder().setType(AclEntryType.USER).setScope(AclEntryScope.ACCESS)
                .setName("alice").setPermission(FsAction.NONE).build();
        when(aclDenied.attributes[2].getAclFeature()).thenReturn(new AclFeature(AclEntryStatusFormat.toInt(Collections.singletonList(alice))));
        assertNativeDenied(aclDenied);
    }

    private static void assertNativeDenied(NativeFixture fixture) throws IOException, ReflectiveOperationException
    {
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        HdfsAccessControlEnforcer enforcer = new HdfsAccessControlEnforcer(nativeChecker(fixture.user), () -> authorizer);
        assertThatThrownBy(() -> fixture.check(enforcer)).isInstanceOf(AccessControlException.class);
        verify(authorizer, never()).authorize(any());
        verify(authorizer).nativeDenied(any(), any(), eq(false));
    }

    private static INodeAttributes attributes(String name, String owner, short permission)
    {
        INodeAttributes attributes = mock(INodeAttributes.class);
        when(attributes.getUserName()).thenReturn(owner);
        when(attributes.getGroupName()).thenReturn("analysts");
        when(attributes.getFsPermission()).thenReturn(new FsPermission(permission));
        when(attributes.getLocalNameBytes()).thenReturn(name.getBytes(StandardCharsets.UTF_8));
        return attributes;
    }

    private static INodeDirectoryAttributes directoryAttributes(String name, String owner, short permission)
    {
        INodeDirectoryAttributes attributes = mock(INodeDirectoryAttributes.class);
        when(attributes.isDirectory()).thenReturn(true);
        when(attributes.getUserName()).thenReturn(owner);
        when(attributes.getGroupName()).thenReturn("analysts");
        when(attributes.getFsPermission()).thenReturn(new FsPermission(permission));
        when(attributes.getLocalNameBytes()).thenReturn(name.getBytes(StandardCharsets.UTF_8));
        return attributes;
    }

    private static INodeDirectory directory(String name)
    {
        INodeDirectory directory = mock(INodeDirectory.class);
        when(directory.isDirectory()).thenReturn(true);
        when(directory.asDirectory()).thenReturn(directory);
        when(directory.getLocalNameBytes()).thenReturn(name.getBytes(StandardCharsets.UTF_8));
        when(directory.getChildrenList(anyInt())).thenReturn(ReadOnlyList.Util.emptyList());
        return directory;
    }

    private static INode file(String name)
    {
        INode file = mock(INode.class);
        when(file.getLocalNameBytes()).thenReturn(name.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static final class NativeFixture
    {
        final UserGroupInformation user = UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"});
        final INode[] inodes = {directory(""), directory("data"), file("file")};
        final INodeAttributes[] attributes = {directoryAttributes("", "bob", (short) 0777), directoryAttributes("data", "bob", (short) 0777),
                attributes("file", "bob", (short) 0666)};
        final byte[][] components = {new byte[0], "data".getBytes(StandardCharsets.UTF_8), "file".getBytes(StandardCharsets.UTF_8)};
        String path = "/data/file";
        boolean checkOwner;
        @org.jspecify.annotations.Nullable FsAction parentAccess;
        @org.jspecify.annotations.Nullable FsAction targetAccess = FsAction.READ;
        @org.jspecify.annotations.Nullable FsAction subAccess;

        @SuppressWarnings("deprecation")
        void check(AccessControlEnforcer enforcer) throws AccessControlException
        {
            // Keep current mutable test attributes and the inode's snapshot view consistent, including replacement directories.
            for (int index = 0; index < inodes.length; index++) {
                when(inodes[index].getSnapshotINode(19)).thenReturn(attributes[index]);
            }
            enforcer.checkPermission("hdfs", "supergroup", user, attributes, inodes, components, 19, path, 1, checkOwner,
                    null, parentAccess, targetAccess, subAccess, false);
        }
    }

    private static AccessControlEnforcer nativeChecker(UserGroupInformation user) throws ReflectiveOperationException
    {
        // Hadoop 2.7 keeps this class package-private; reflection lets the same test exercise the actual native
        // checker in every adapter without binding production code to that visibility or internal constructor.
        Class<?> type = Class.forName("org.apache.hadoop.hdfs.server.namenode.FSPermissionChecker");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class, UserGroupInformation.class,
                INodeAttributeProvider.class);
        constructor.setAccessible(true);
        return (AccessControlEnforcer) constructor.newInstance("hdfs", "supergroup", user, null);
    }
}
