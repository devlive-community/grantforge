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
import org.apache.hadoop.ipc.CallerContext;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizationContext;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class HdfsAccessControlEnforcerTest
{
    private static AuthorizationContext context()
    {
        return new AuthorizationContext.Builder().fsOwner("hdfs").supergroup("supergroup")
                .callerUgi(UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"}))
                .inodeAttrs(new INodeAttributes[0]).inodes(new INode[0]).pathByNameArr(new byte[0][]).path("/data/file")
                .snapshotId(19).ancestorIndex(-1).access(FsAction.READ).operationName("open")
                .callerContext(new CallerContext.Builder("client-request").build()).build();
    }

    @Test
    void declaresTheContextCallbackRequiredByTheNameNodeStartupProbe() throws ReflectiveOperationException
    {
        assertThat(HdfsAccessControlEnforcer.class.getDeclaredMethod("checkPermissionWithContext", AuthorizationContext.class)).isNotNull();
        Set<String> methods = Arrays.stream(AccessControlEnforcer.class.getMethods()).map(Method::getName).collect(Collectors.toSet());
        assertThat(methods).contains("checkPermission", "checkPermissionWithContext").contains("checkSuperUserPermissionWithContext", "denyUserAccess");
    }

    @Test
    void retainsTheNativeContextGateAndPreservesOperationAndCallerContext() throws IOException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        HdfsAccessControlEnforcer enforcer = new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer);
        AuthorizationContext context = context();
        enforcer.checkPermissionWithContext(context);
        verify(nativeChecks).checkPermissionWithContext(context);
        ArgumentCaptor<HdfsAuthorizationContext> converted = ArgumentCaptor.forClass(HdfsAuthorizationContext.class);
        verify(authorizer).authorize(converted.capture());
        assertThat(converted.getValue().operation()).isEqualTo("open");
        assertThat(converted.getValue().callerContext()).isEqualTo("client-request");
        assertThat(converted.getValue().path()).isEqualTo("/data/file");
        assertThat(converted.getValue().access()).isEqualTo(4);
        assertThat(converted.getValue().snapshotId()).isEqualTo(19);

        doThrow(new AccessControlException("native context refused")).when(nativeChecks).checkPermissionWithContext(context);
        assertThatThrownBy(() -> enforcer.checkPermissionWithContext(context)).isInstanceOf(AccessControlException.class)
                .hasMessage("native context refused");
        verify(authorizer).nativeDenied(any(), eq("native context refused"), eq(false));
    }

    @Test
    void enforcesSuperuserReadWithoutWriteAndCannotBypassANativeSuperuserDenial() throws IOException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        HdfsAccessControlEnforcer enforcer = new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer);
        AuthorizationContext context = context();
        context.setInodes(null);
        context.setPathByNameArr(null);
        enforcer.checkSuperUserPermissionWithContext(context);
        verify(nativeChecks).checkSuperUserPermissionWithContext(context);
        verify(authorizer).authorizeSuperuser(any(), eq(4));
        doThrow(new AccessControlException("native superuser refused")).when(nativeChecks).checkSuperUserPermissionWithContext(context);
        assertThatThrownBy(() -> enforcer.checkSuperUserPermissionWithContext(context)).isInstanceOf(AccessControlException.class)
                .hasMessage("native superuser refused");
        verify(authorizer).nativeDenied(any(), eq("native superuser refused"), eq(true));
    }

    @Test
    void denyNotificationAlwaysRejectsAndNeverAttemptsANativeOrPolicyAllow() throws IOException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        HdfsAccessControlEnforcer enforcer = new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer);
        assertThatThrownBy(() -> enforcer.denyUserAccess(context(), "unreadable by superuser"))
                .isInstanceOf(AccessControlException.class).hasMessage("unreadable by superuser");
        verify(authorizer).nativeDenied(any(), eq("unreadable by superuser"), eq(true));
        verify(authorizer, never()).authorize(any());
        verify(authorizer, never()).authorizeSuperuser(any(), anyInt());
        verifyNoInteractions(nativeChecks);
    }
}
