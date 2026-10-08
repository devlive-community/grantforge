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
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizationContext;
import org.devlive.grantforge.hdfs.common.HdfsAuthorizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertThat(methods).contains("checkPermission", "checkPermissionWithContext").doesNotContain("checkSuperUserPermissionWithContext", "denyUserAccess");
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
    void authorizesARealReadWithoutOptionalCallerContextAndLeavesAuditDetailsAbsent() throws IOException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        AgentDecision allowed = mock(AgentDecision.class);
        when(allowed.allowed()).thenReturn(true);
        when(allowed.determined()).thenReturn(true);
        when(allowed.outcome()).thenReturn(AgentDecision.Outcome.ALLOWED);
        List<AccessEvent> events = new ArrayList<>();
        HdfsAuthorizer authorizer = spy(new HdfsAuthorizer(request -> allowed, events::add, false));
        AuthorizationContext context = context();
        context.setCallerContext(null);
        context.setInodes(new INode[] {mock(INode.class)});
        context.setInodeAttrs(new INodeAttributes[] {mock(INodeAttributes.class)});
        context.setPathByNameArr(new byte[][] {"data".getBytes(StandardCharsets.UTF_8), "file".getBytes(StandardCharsets.UTF_8)});

        new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer).checkPermissionWithContext(context);

        verify(nativeChecks).checkPermissionWithContext(context);
        ArgumentCaptor<HdfsAuthorizationContext> converted = ArgumentCaptor.forClass(HdfsAuthorizationContext.class);
        verify(authorizer).authorize(converted.capture());
        assertThat(converted.getValue().callerContext()).isNull();
        assertThat(events).hasSize(1);
        assertThat(ReflectionTestUtils.getField(events.get(0), "allowed")).isEqualTo(true);
        assertThat(ReflectionTestUtils.getField(events.get(0), "request")).isNull();
    }

    @Test
    void sparseNativeRejectionRetainsItsReasonWithoutInventingPolicyAccess() throws IOException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAuthorizer authorizer = mock(HdfsAuthorizer.class);
        AuthorizationContext context = context();
        context.setCallerContext(null);
        context.setInodes(null);
        context.setPathByNameArr(null);
        AccessControlException refused = new AccessControlException("native path rejected");
        doThrow(refused).when(nativeChecks).checkPermissionWithContext(context);

        assertThatThrownBy(() -> new HdfsAccessControlEnforcer(nativeChecks, () -> authorizer).checkPermissionWithContext(context))
                .isSameAs(refused);

        ArgumentCaptor<HdfsAuthorizationContext> converted = ArgumentCaptor.forClass(HdfsAuthorizationContext.class);
        verify(authorizer).nativeDenied(converted.capture(), eq("native path rejected"), eq(false));
        assertThat(converted.getValue().nodes()).isEmpty();
        assertThat(converted.getValue().components()).isEmpty();
        assertThat(converted.getValue().callerContext()).isNull();
        verify(authorizer, never()).authorize(any());
    }
}
