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
import org.apache.hadoop.hdfs.server.namenode.FSPermissionChecker;
import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AccessControlEnforcer;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributeProvider.AuthorizationContext;
import org.apache.hadoop.hdfs.server.namenode.INodeAttributes;
import org.apache.hadoop.hdfs.server.namenode.INodeDirectory;
import org.apache.hadoop.hdfs.util.ReadOnlyList;
import org.apache.hadoop.ipc.CallerContext;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HdfsAccessControlEnforcerTest
{
    private final List<AccessRequest> requests = new ArrayList<>();
    private final List<AccessEvent> events = new ArrayList<>();

    static AgentDecision decision(String outcome)
    {
        AgentDecision decision = mock(AgentDecision.class);
        AgentDecision.Outcome state = AgentDecision.Outcome.valueOf(outcome);
        when(decision.outcome()).thenReturn(state);
        when(decision.allowed()).thenReturn(state == AgentDecision.Outcome.ALLOWED);
        when(decision.determined()).thenReturn(state != AgentDecision.Outcome.NOT_DETERMINED);
        when(decision.policyId()).thenReturn(state == AgentDecision.Outcome.NOT_DETERMINED ? null : 42L);
        when(decision.policyVersion()).thenReturn(7L);
        return decision;
    }

    static AuthorizationContext context(@Nullable FsAction action)
    {
        INodeDirectory root = directory("");
        INodeDirectory data = directory("data");
        INode file = file("file");
        return new AuthorizationContext.Builder().fsOwner("hdfs").supergroup("supergroup")
                .callerUgi(UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"}))
                .inodeAttrs(new INodeAttributes[] {attributes("bob", (short) 0777), attributes("bob", (short) 0777),
                        attributes("bob", (short) 0666)})
                .inodes(new INode[] {root, data, file}).pathByNameArr(components("", "data", "file"))
                .path("/data/file").snapshotId(19).ancestorIndex(1).access(action)
                .operationName("open").callerContext(new CallerContext.Builder("client-request").build()).build();
    }

    private static byte[][] components(String... names)
    {
        byte[][] components = new byte[names.length][];
        for (int index = 0; index < names.length; index++) {
            components[index] = names[index].getBytes(StandardCharsets.UTF_8);
        }
        return components;
    }

    private static INodeAttributes attributes(String owner, short permission)
    {
        INodeAttributes attributes = mock(INodeAttributes.class);
        when(attributes.getUserName()).thenReturn(owner);
        when(attributes.getGroupName()).thenReturn("analysts");
        when(attributes.getFsPermission()).thenReturn(new FsPermission(permission));
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

    private HdfsAccessControlEnforcer enforcer(@Nullable AccessControlEnforcer nativeChecks, Function<AccessRequest, AgentDecision> decide,
            boolean fallback)
    {
        return new HdfsAccessControlEnforcer(nativeChecks, request -> {
            requests.add(request);
            return decide.apply(request);
        }, events::add, () -> fallback);
    }

    private HdfsAccessControlEnforcer allowing(@Nullable AccessControlEnforcer nativeChecks)
    {
        return enforcer(nativeChecks, request -> decision("ALLOWED"), false);
    }

    private List<String> checks()
    {
        return requests.stream().map(request -> request.resource().get("path") + ":" + request.accessType()).toList();
    }

    private static @Nullable Object field(AccessEvent event, String name)
    {
        return ReflectionTestUtils.getField(event, name);
    }

    @ParameterizedTest
    @EnumSource(FsAction.class)
    void checksEveryRequestedActionBitAsWellAsTraversal(FsAction action) throws AccessControlException
    {
        AuthorizationContext context = context(action);
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        allowing(nativeChecks).checkPermissionWithContext(context);

        verify(nativeChecks).checkPermissionWithContext(context);
        assertThat(checks()).contains("/:execute", "/data:execute");
        for (String type : List.of("read", "write", "execute")) {
            FsAction bit = switch (type) {
                case "read" -> FsAction.READ;
                case "write" -> FsAction.WRITE;
                default -> FsAction.EXECUTE;
            };
            boolean expected = action.implies(bit);
            assertThat(checks().contains("/data/file:" + type)).isEqualTo(expected);
        }
        assertThat(requests.get(0).user()).isEqualTo("alice");
        assertThat(requests.get(0).groups()).containsExactly("analysts");
        assertThat(requests.get(0).context()).containsEntry("operation", "open");
        assertThat(events).hasSameSizeAs(requests);
        assertThat(field(events.get(0), "byGrantForge")).isEqualTo(true);
        assertThat(field(events.get(0), "policyId")).isEqualTo(42L);
        assertThat(field(events.get(0), "policyVersion")).isEqualTo(7L);
        assertThat(field(events.get(0), "request")).isEqualTo("client-request");
    }

    @Test
    void deniesACombinedPermissionWhenAnyOneBitIsDenied()
    {
        HdfsAccessControlEnforcer enforcer = enforcer(mock(AccessControlEnforcer.class), request ->
                decision(request.accessType().equals("write") ? "DENIED" : "ALLOWED"), true);

        assertThatThrownBy(() -> enforcer.checkPermissionWithContext(context(FsAction.READ_WRITE)))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("write");
        assertThat(events).hasSize(1);
        assertThat(field(events.get(0), "allowed")).isEqualTo(false);
        assertThat(field(events.get(0), "resource")).isEqualTo("/data/file");
        assertThat(field(events.get(0), "accessType")).isEqualTo("write");
    }

    @Test
    void doesNotFallBackOnExplicitDenyAndOnlyFallsBackOnUndeterminedAfterNativeChecks() throws AccessControlException
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAccessControlEnforcer strict = enforcer(nativeChecks, request -> decision("NOT_DETERMINED"), false);
        assertThatThrownBy(() -> strict.checkPermissionWithContext(context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("NOT_DETERMINED");
        events.clear();
        HdfsAccessControlEnforcer fallback = enforcer(nativeChecks, request -> decision("NOT_DETERMINED"), true);
        fallback.checkPermissionWithContext(context(FsAction.READ));
        assertThat(field(events.get(0), "byGrantForge")).isEqualTo(false);
        assertThat(field(events.get(0), "allowed")).isEqualTo(true);
        HdfsAccessControlEnforcer denied = enforcer(nativeChecks, request -> decision("DENIED"), true);
        assertThatThrownBy(() -> denied.checkPermissionWithContext(context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("DENIED");
    }

    @Test
    void checksParentAndExistingAncestorAndTheMissingCreateTarget() throws AccessControlException
    {
        AuthorizationContext context = context(null);
        context.setInodes(new INode[] {directory(""), directory("data"), null, null});
        context.setInodeAttrs(new INodeAttributes[] {attributes("bob", (short) 0777), attributes("bob", (short) 0777), null, null});
        context.setPathByNameArr(components("", "data", "new", "file"));
        context.setPath("/data/new/file");
        context.setAncestorIndex(2);
        context.setAncestorAccess(FsAction.WRITE);
        context.setParentAccess(FsAction.WRITE);
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context);

        assertThat(checks()).containsExactly("/:execute", "/data:execute", "/data:write", "/data/new/file:write");
        requests.clear();
        AuthorizationContext existing = context(null);
        existing.setParentAccess(FsAction.WRITE_EXECUTE);
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(existing);
        assertThat(checks()).contains("/data:write", "/data:execute", "/data/file:write", "/data/file:execute");
    }

    @Test
    void enforcesOwnerOnlyOperationsAsWriteAndAllowsPureCreateTraversalWithoutTargetRead() throws AccessControlException
    {
        AuthorizationContext owner = context(null);
        owner.setDoCheckOwner(true);
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(owner);
        assertThat(checks()).contains("/data/file:write").doesNotContain("/data/file:read");
        requests.clear();
        AuthorizationContext createTraversal = context(null);
        createTraversal.setOperationName("create");
        enforcer(mock(AccessControlEnforcer.class), request -> decision(
                "execute".equals(request.accessType()) ? "ALLOWED" : "DENIED"), false)
                .checkPermissionWithContext(createTraversal);
        assertThat(checks()).containsExactly("/:execute", "/data:execute").doesNotContain("/data/file:read");
    }

    @Test
    void handlesSingleInodeChecksAndNativeDenialWithNullPath() throws AccessControlException
    {
        AuthorizationContext context = context(FsAction.READ);
        context.setInodes(new INode[] {context.getInodes()[2]});
        context.setInodeAttrs(new INodeAttributes[] {context.getInodeAttrs()[2]});
        context.setAncestorIndex(-1);
        context.setPath(null);
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context);
        assertThat(checks()).containsExactly("/data/file:read");
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        doThrow(new AccessControlException("native refused")).when(nativeChecks).checkPermissionWithContext(any());
        assertThatThrownBy(() -> allowing(nativeChecks).checkPermissionWithContext(context)).isInstanceOf(AccessControlException.class);
        assertThat(field(events.get(events.size() - 1), "resource")).isEqualTo("/data/file");
    }

    @Test
    void handlesRootOnlyNullComponent() throws AccessControlException
    {
        AuthorizationContext context = context(FsAction.READ_EXECUTE);
        context.setInodes(new INode[] {context.getInodes()[0]});
        context.setInodeAttrs(new INodeAttributes[] {context.getInodeAttrs()[0]});
        context.setPathByNameArr(new byte[][] {null});
        context.setAncestorIndex(-1);
        context.setPath(null);
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context);
        assertThat(checks()).containsExactly("/:read", "/:execute");
    }

    @Test
    void rootNoneStillRequiresAPolicyAndMalformedEmptyContextFailsClosed()
    {
        AuthorizationContext context = context(FsAction.NONE);
        context.setInodes(new INode[] {context.getInodes()[0]});
        context.setInodeAttrs(new INodeAttributes[] {context.getInodeAttrs()[0]});
        context.setPathByNameArr(new byte[][] {null});
        context.setAncestorIndex(-1);
        context.setPath("/");
        assertThatThrownBy(() -> enforcer(mock(AccessControlEnforcer.class), request -> decision("NOT_DETERMINED"), false)
                .checkPermissionWithContext(context)).isInstanceOf(AccessControlException.class);
        assertThat(checks()).containsExactly("/:execute");
        context.setInodes(new INode[0]);
        assertThatThrownBy(() -> allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("no inode path");
    }

    @Test
    @SuppressWarnings("deprecation")
    void passesEveryLegacyArgumentUnchangedAndAppliesTheSamePolicyChecks() throws AccessControlException
    {
        AuthorizationContext context = context(FsAction.READ);
        context.setDoCheckOwner(true);
        context.setAncestorAccess(FsAction.WRITE);
        context.setParentAccess(FsAction.WRITE_EXECUTE);
        context.setSubAccess(FsAction.READ_EXECUTE);
        context.setIgnoreEmptyDir(true);
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        allowing(nativeChecks).checkPermission(context.getFsOwner(), context.getSupergroup(), context.getCallerUgi(),
                context.getInodeAttrs(), context.getInodes(), context.getPathByNameArr(), context.getSnapshotId(), context.getPath(),
                context.getAncestorIndex(), context.isDoCheckOwner(), context.getAncestorAccess(), context.getParentAccess(),
                context.getAccess(), context.getSubAccess(), context.isIgnoreEmptyDir());
        verify(nativeChecks).checkPermission(context.getFsOwner(), context.getSupergroup(), context.getCallerUgi(),
                context.getInodeAttrs(), context.getInodes(), context.getPathByNameArr(), context.getSnapshotId(), context.getPath(),
                context.getAncestorIndex(), context.isDoCheckOwner(), context.getAncestorAccess(), context.getParentAccess(),
                context.getAccess(), context.getSubAccess(), context.isIgnoreEmptyDir());
        assertThat(checks()).contains("/data:write", "/data/file:write", "/data/file:read", "/data/file:execute");
    }

    @Test
    void checksEverySubtreeEntryWithTheSnapshotIdIncludingEmptyDirectoriesAndFiles() throws AccessControlException
    {
        INodeDirectory directory = directory("private");
        INodeDirectory empty = directory("empty");
        INode file = file("secret");
        when(directory.getChildrenList(19)).thenReturn(ReadOnlyList.Util.asReadOnlyList(List.of(empty, file)));
        AuthorizationContext context = context(null);
        context.getInodes()[2] = directory;
        context.setPath("/data/private");
        context.setPathByNameArr(components("", "data", "private"));
        context.setSubAccess(FsAction.ALL);
        context.setIgnoreEmptyDir(true);
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        allowing(nativeChecks).checkPermissionWithContext(context);

        verify(nativeChecks).checkPermissionWithContext(context);
        verify(directory).getChildrenList(19);
        verify(empty).getChildrenList(19);
        for (String path : List.of("/data/private", "/data/private/empty", "/data/private/secret")) {
            assertThat(checks()).contains(path + ":read", path + ":write", path + ":execute");
        }
        requests.clear();
        HdfsAccessControlEnforcer denied = enforcer(nativeChecks, request -> decision(
                requireNonNull(request.resource().get("path")).endsWith("/secret") ? "DENIED" : "ALLOWED"), true);
        assertThatThrownBy(() -> denied.checkPermissionWithContext(context)).isInstanceOf(AccessControlException.class)
                .hasMessageContaining("/data/private/secret");
    }

    @Test
    void boundsSubtreeWorkBeforeAllocatingPermissionChecks()
    {
        INodeDirectory directory = directory("large");
        INode child = file("child");
        when(directory.getChildrenList(19)).thenReturn(ReadOnlyList.Util.asReadOnlyList(Collections.nCopies(100_001, child)));
        AuthorizationContext context = context(null);
        context.getInodes()[2] = directory;
        context.setSubAccess(FsAction.ALL);
        assertThatThrownBy(() -> allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("subtree exceeds 100000");
        assertThat(requests).isEmpty();
        assertThat(field(events.get(0), "byGrantForge")).isEqualTo(true);
    }

    @Test
    void nativeTraversalOwnerStickyBitAndSubtreeDenialsCannotBeOverriddenByAnAllowPolicy()
    {
        NativeChecker nativeChecks = new NativeChecker();
        AuthorizationContext traversal = context(FsAction.READ);
        traversal.getInodeAttrs()[0] = attributes("bob", (short) 0000);
        assertNativeDenied(nativeChecks, traversal);
        AuthorizationContext owner = context(FsAction.READ);
        owner.setDoCheckOwner(true);
        assertNativeDenied(nativeChecks, owner);
        AuthorizationContext sticky = context(null);
        sticky.setParentAccess(FsAction.WRITE);
        sticky.getInodeAttrs()[1] = attributes("bob", (short) 01777);
        assertNativeDenied(nativeChecks, sticky);
        INodeDirectory restricted = directory("restricted");
        org.apache.hadoop.hdfs.server.namenode.INodeDirectoryAttributes restrictedAttributes = directoryAttributes("bob", (short) 0000);
        when(restricted.getSnapshotINode(19)).thenReturn(restrictedAttributes);
        AuthorizationContext subtree = context(null);
        subtree.getInodes()[2] = restricted;
        subtree.setSubAccess(FsAction.ALL);
        assertNativeDenied(nativeChecks, subtree);
    }

    @Test
    void trueNativeReadSucceedsWhileNativeModeAndAclDenyOverrideAnAllowPolicy() throws AccessControlException
    {
        NativeChecker nativeChecks = new NativeChecker();
        allowing(nativeChecks).checkPermissionWithContext(context(FsAction.READ));
        assertThat(checks()).contains("/data/file:read");
        AuthorizationContext noRead = context(FsAction.READ);
        noRead.getInodeAttrs()[2] = attributes("bob", (short) 0000);
        assertNativeDenied(nativeChecks, noRead);
        AuthorizationContext aclDeny = context(FsAction.READ);
        AclEntry alice = new AclEntry.Builder().setType(AclEntryType.USER).setScope(AclEntryScope.ACCESS)
                .setName("alice").setPermission(FsAction.NONE).build();
        when(aclDeny.getInodeAttrs()[2].getAclFeature()).thenReturn(new AclFeature(AclEntryStatusFormat.toInt(List.of(alice))));
        assertNativeDenied(nativeChecks, aclDeny);
    }

    private static org.apache.hadoop.hdfs.server.namenode.INodeDirectoryAttributes directoryAttributes(String owner, short mode)
    {
        org.apache.hadoop.hdfs.server.namenode.INodeDirectoryAttributes attributes =
                mock(org.apache.hadoop.hdfs.server.namenode.INodeDirectoryAttributes.class);
        when(attributes.getUserName()).thenReturn(owner);
        when(attributes.getGroupName()).thenReturn("analysts");
        when(attributes.getFsPermission()).thenReturn(new FsPermission(mode));
        return attributes;
    }

    private void assertNativeDenied(AccessControlEnforcer nativeChecks, AuthorizationContext context)
    {
        requests.clear();
        assertThatThrownBy(() -> allowing(nativeChecks).checkPermissionWithContext(context)).isInstanceOf(AccessControlException.class);
        assertThat(requests).isEmpty();
        assertThat(field(events.get(events.size() - 1), "allowed")).isEqualTo(false);
        assertThat(field(events.get(events.size() - 1), "byGrantForge")).isEqualTo(false);
    }

    @Test
    void respectsNativeSuperuserPrivilegeAndConservativelyChecksPathPermissions() throws AccessControlException
    {
        AuthorizationContext context = context(null);
        assertThatThrownBy(() -> allowing(new NativeChecker()).checkSuperUserPermissionWithContext(context))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("Superuser");
        assertThat(requests).isEmpty();
        context.setCallerUgi(UserGroupInformation.createUserForTesting("hdfs", new String[] {"supergroup"}));
        allowing(new NativeChecker()).checkSuperUserPermissionWithContext(context);
        assertThat(checks()).containsExactly("/data/file:read", "/data/file:write", "/data/file:execute");
        requests.clear();
        context.setPath(null);
        allowing(new NativeChecker()).checkSuperUserPermissionWithContext(context);
        assertThat(requests).isEmpty();
        assertThat(field(events.get(events.size() - 1), "byGrantForge")).isEqualTo(false);
    }

    @Test
    void denyCallbackAlwaysDeniesAndAuditsAndPolicyEvaluationFailureFailsClosed()
    {
        AccessControlEnforcer nativeChecks = mock(AccessControlEnforcer.class);
        HdfsAccessControlEnforcer enforcer = allowing(nativeChecks);
        assertThatThrownBy(() -> enforcer.denyUserAccess(context(FsAction.READ), "not owner"))
                .isInstanceOf(AccessControlException.class).hasMessage("not owner");
        verifyNoInteractions(nativeChecks);
        assertThat(requests).isEmpty();
        assertThat(field(events.get(0), "allowed")).isEqualTo(false);
        HdfsAccessControlEnforcer broken = enforcer(nativeChecks, request -> {
            throw new IllegalStateException("broken engine");
        }, true);
        assertThatThrownBy(() -> broken.checkPermissionWithContext(context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class).hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> allowing(null).checkPermissionWithContext(context(FsAction.READ)))
                .isInstanceOf(AccessControlException.class).hasMessageContaining("native permission");
    }

    @Test
    void doesNotChangeDecisionsWhenAuditQueueThrowsAndBoundsOnlyTheAuditResource() throws AccessControlException
    {
        HdfsAccessControlEnforcer enforcer = new HdfsAccessControlEnforcer(mock(AccessControlEnforcer.class),
                request -> decision("ALLOWED"), event -> { throw new IllegalStateException("audit failed"); }, () -> false);
        enforcer.checkPermissionWithContext(context(FsAction.READ));
        AuthorizationContext context = context(FsAction.READ);
        context.setPath("/" + "a".repeat(1200));
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context);
        assertThat(requests.get(requests.size() - 1).resource().get("path")).hasSize(1201);
        AccessEvent event = events.get(events.size() - 1);
        assertThat((String) field(event, "resource")).hasSizeLessThanOrEqualTo(1000).endsWith("...[truncated]");
        assertThat((String) field(event, "request")).contains("resourceLength=1201", "resourceSha256=");
    }

    @Test
    void realPathPolicyCanDenyAChildWithoutDenyingItsParent()
    {
        Snapshot snapshot = Snapshot.parse("""
                {"format":1,"service":"cluster","serviceType":"hdfs","serviceEnabled":true,"policyVersion":1,
                 "definition":{"resources":[{"name":"path","parent":null,"matcher":"PATH","caseSensitive":true}],
                   "accessTypes":[{"name":"read","impliedGrants":[]},{"name":"write","impliedGrants":[]},
                     {"name":"execute","impliedGrants":[]}],"conditions":[]},
                 "policies":[{"id":"1","type":"ACCESS","name":"protected file","priority":"NORMAL","document":{
                   "resources":{"path":{"values":["/data/private/secret"],"excludes":false,"recursive":false}},
                   "deny":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["write"]}]}}],
                 "roles":{},"groups":{}}
                """.getBytes(StandardCharsets.UTF_8), Map.of());
        INodeDirectory directory = directory("private");
        INode secret = file("secret");
        when(directory.getChildrenList(19)).thenReturn(ReadOnlyList.Util.asReadOnlyList(List.of(secret)));
        AuthorizationContext context = context(null);
        context.getInodes()[2] = directory;
        context.setPath("/data/private");
        context.setSubAccess(FsAction.ALL);

        assertThatThrownBy(() -> enforcer(mock(AccessControlEnforcer.class), snapshot::decide, true)
                .checkPermissionWithContext(context)).isInstanceOf(AccessControlException.class)
                .hasMessageContaining("write").hasMessageContaining("/data/private/secret");
    }

    @Test
    void snapshotsAlsoCheckTheLivePathAndCannotBypassAnExactFileDeny() throws AccessControlException
    {
        Snapshot snapshot = Snapshot.parse("""
                {"format":1,"service":"cluster","serviceType":"hdfs","serviceEnabled":true,"policyVersion":1,
                 "definition":{"resources":[{"name":"path","parent":null,"matcher":"PATH","caseSensitive":true}],
                   "accessTypes":[{"name":"read","impliedGrants":[]},{"name":"write","impliedGrants":[]},
                     {"name":"execute","impliedGrants":[]}],"conditions":[]},
                 "policies":[{"id":"1","type":"ACCESS","name":"broad allow","priority":"NORMAL","document":{
                   "resources":{"path":{"values":["/"],"excludes":false,"recursive":true}},
                   "allow":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["read","execute"]}]}},
                   {"id":"2","type":"ACCESS","name":"exact deny","priority":"NORMAL","document":{
                   "resources":{"path":{"values":["/data/file"],"excludes":false,"recursive":false}},
                   "deny":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["read"]}]}}],
                 "roles":{},"groups":{}}
                """.getBytes(StandardCharsets.UTF_8), Map.of());
        AuthorizationContext context = context(FsAction.READ);
        context.setPath("/data/.snapshot/s1/file");
        context.setPathByNameArr(components("", "data", ".snapshot/s1", "file"));
        context.setInodes(new INode[] {directory(""), directory("data"), directory("data"), file("file")});
        context.setInodeAttrs(new INodeAttributes[] {attributes("bob", (short) 0777), attributes("bob", (short) 0777),
                attributes("bob", (short) 0777), attributes("bob", (short) 0666)});
        context.setAncestorIndex(2);
        assertThatThrownBy(() -> enforcer(mock(AccessControlEnforcer.class), snapshot::decide, false)
                .checkPermissionWithContext(context)).isInstanceOf(AccessControlException.class).hasMessageContaining("/data/file");
        assertThat(checks()).contains("/data/.snapshot/s1/file:read", "/data/file:read", "/data/.snapshot/s1:execute");
        requests.clear();
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context);
        assertThat(checks()).contains("/data/.snapshot/s1/file:read", "/data/file:read");
        requests.clear();
        context.setPath("/data/.snapshot");
        allowing(mock(AccessControlEnforcer.class)).checkPermissionWithContext(context);
        assertThat(checks()).contains("/data/.snapshot:read", "/data:read");
    }

    private static final class NativeChecker
            extends FSPermissionChecker
    {
        NativeChecker()
        {
            super("hdfs", "supergroup", UserGroupInformation.createUserForTesting("alice", new String[] {"analysts"}), null);
        }
    }
}
