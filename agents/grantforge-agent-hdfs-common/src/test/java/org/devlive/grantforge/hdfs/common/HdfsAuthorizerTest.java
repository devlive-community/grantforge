// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsAuthorizerTest
{
    private final List<AccessRequest> requests = new ArrayList<>();
    private final List<AccessEvent> events = new ArrayList<>();

    static Snapshot snapshot(long version, String serviceType, @Nullable String deniedPath, String deniedAccess)
    {
        String deny = deniedPath == null ? "" : """
                ,{"id":"2","name":"restriction","type":"ACCESS","priority":"NORMAL","document":{
                  "resources":{"path":{"values":["%s"],"recursive":false,"excludes":false}},
                  "deny":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["%s"]}]}}
                """.formatted(deniedPath, deniedAccess);
        return Snapshot.parse("""
                {"format":1,"service":"cluster","serviceType":"%s","serviceEnabled":true,"policyVersion":%d,
                 "definition":{"resources":[{"name":"path","parent":null,"matcher":"PATH","caseSensitive":true}],
                  "accessTypes":[{"name":"read","impliedGrants":[]},{"name":"write","impliedGrants":[]},
                   {"name":"execute","impliedGrants":[]}],"conditions":[]},
                 "policies":[{"id":"1","name":"base","type":"ACCESS","priority":"NORMAL","document":{
                  "resources":{"path":{"values":["/"],"recursive":true,"excludes":false}},
                  "allow":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["read","write","execute"]}]}}%s],
                 "roles":{},"groups":{}}
                """.formatted(serviceType, version, deny).getBytes(StandardCharsets.UTF_8), Map.of());
    }

    static HdfsNode node(String name, boolean directory, List<HdfsNode> children)
    {
        return new HdfsNode()
        {
            @Override
            public boolean isDirectory()
            {
                return directory;
            }

            @Override
            public String name()
            {
                return name;
            }

            @Override
            public List<HdfsNode> children(int snapshotId)
            {
                return children;
            }
        };
    }

    static byte[][] components(String... elements)
    {
        return Arrays.stream(elements).map(element -> element.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
    }

    static HdfsAuthorizationContext.Builder context(int action)
    {
        return HdfsAuthorizationContext.builder("alice", "analysts")
                .nodes(new HdfsNode[] {node("", true, List.of()), node("data", true, List.of()), node("file", false, List.of())})
                .components(components("", "data", "file")).path("/data/file").snapshotId(19).ancestorIndex(1)
                .actions(0, 0, action, 0).operation("open").clientIp("10.2.3.4").callerContext("client-request");
    }

    private HdfsAuthorizer authorizer(Function<AccessRequest, AgentDecision> session, boolean fallback)
    {
        return new HdfsAuthorizer(request -> {
            requests.add(request);
            return session.apply(request);
        }, events::add, fallback);
    }

    private List<String> checks()
    {
        return requests.stream().map(request -> request.resource().get("path") + ":" + request.accessType()).toList();
    }

    static @Nullable Object field(AccessEvent event, String name)
    {
        return ReflectionTestUtils.getField(event, name);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void checksAllRequestedBitsAndCapturesIdentityTimeAndContext(int mask) throws IOException
    {
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(context(mask).build());
        assertThat(checks()).contains("/:execute", "/data:execute");
        for (String access : List.of("read", "write", "execute")) {
            int bit = "read".equals(access) ? 4 : "write".equals(access) ? 2 : 1;
            assertThat(checks().contains("/data/file:" + access)).isEqualTo((mask & bit) != 0);
        }
        assertThat(requests.get(0).user()).isEqualTo("alice");
        assertThat(requests.get(0).groups()).containsExactly("analysts");
        assertThat(requests.get(0).context()).containsEntry("clientAddress", "10.2.3.4")
                .containsEntry("operation", "open").containsEntry("callerContext", "client-request");
        assertThat(requests).allMatch(request -> request.time().equals(requests.get(0).time()));
        assertThat(events).hasSameSizeAs(requests);
        assertThat(field(events.get(0), "policyVersion")).isEqualTo(7L);
        assertThat(field(events.get(0), "clientIp")).isEqualTo("10.2.3.4");
    }

    @Test
    void strictModeDeniesMissingPoliciesAndFallbackNeverOverridesExplicitDeny() throws IOException
    {
        assertThatThrownBy(() -> authorizer(request -> AgentDecision.withoutSnapshot(), false).authorize(context(4).build()))
                .isInstanceOf(IOException.class).hasMessageContaining("NOT_DETERMINED");
        assertThat(field(events.get(0), "byGrantForge")).isEqualTo(true);
        events.clear();
        authorizer(request -> AgentDecision.withoutSnapshot(), true).authorize(context(4).build());
        assertThat(field(events.get(0), "byGrantForge")).isEqualTo(false);
        events.clear();
        assertThatThrownBy(() -> authorizer(snapshot(7, "hdfs", "/data/file", "write")::decide, true)
                .authorize(context(6).build())).isInstanceOf(IOException.class).hasMessageContaining("write");
        assertThat(events).hasSize(1);
        assertThat(field(events.get(0), "allowed")).isEqualTo(false);
    }

    @Test
    void appliesParentAndExistingAncestorActionsToTheMutationTargetWithoutInventingRead() throws IOException
    {
        HdfsAuthorizationContext missing = context(0).nodes(new HdfsNode[] {node("", true, List.of()), node("data", true, List.of()), null, null})
                .components(components("", "data", "new", "file")).path("/data/new/file").ancestorIndex(2).actions(2, 2, 0, 0).build();
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(missing);
        assertThat(checks()).containsExactly("/:execute", "/data:execute", "/data:write", "/data/new/file:write");
        requests.clear();
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(context(0).actions(0, 3, 0, 0).build());
        assertThat(checks()).contains("/data:write", "/data/file:write", "/data/file:execute").doesNotContain("/data/file:read");
        requests.clear();
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(context(0).checkOwner(true).build());
        assertThat(checks()).contains("/data/file:write");
    }

    @Test
    void rootNoneSingleInodeAndMalformedCallbacksFailSafely() throws IOException
    {
        HdfsAuthorizationContext root = context(0).nodes(new HdfsNode[] {node("", true, List.of())}).components(new byte[][] {null})
                .path(null).ancestorIndex(-1).build();
        assertThatThrownBy(() -> authorizer(request -> AgentDecision.withoutSnapshot(), false).authorize(root))
                .isInstanceOf(IOException.class);
        assertThat(checks()).containsExactly("/:execute");
        requests.clear();
        HdfsAuthorizationContext single = context(4).nodes(new HdfsNode[] {node("file", false, List.of())})
                .path(null).ancestorIndex(-1).build();
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(single);
        assertThat(checks()).containsExactly("/data/file:read");
        events.clear();
        HdfsAuthorizer overlay = authorizer(snapshot(7, "hdfs", null, "read")::decide, false);
        overlay.nativeDenied(single, "native denied");
        assertThat(field(events.get(0), "resource")).isEqualTo("/data/file");
        assertThat(field(events.get(0), "byGrantForge")).isEqualTo(false);
        assertThatThrownBy(() -> overlay.authorize(HdfsAuthorizationContext.builder("alice").build()))
                .isInstanceOf(IOException.class).hasMessageContaining("no inode path");
    }

    @Test
    void appliesSubtreeDeniesToFilesAndEmptyDirectoriesAndUsesTheSnapshotId() throws IOException
    {
        AtomicInteger requestedSnapshot = new AtomicInteger();
        List<HdfsNode> children = List.of(node("empty", true, List.of()), node("secret", false, List.of()));
        HdfsNode tree = new HdfsNode()
        {
            @Override
            public boolean isDirectory()
            {
                return true;
            }

            @Override
            public String name()
            {
                return "private";
            }

            @Override
            public List<HdfsNode> children(int snapshotId)
            {
                requestedSnapshot.set(snapshotId);
                return children;
            }
        };
        HdfsAuthorizationContext callback = context(0).nodes(new HdfsNode[] {node("", true, List.of()), node("data", true, List.of()), tree})
                .path("/data/private").actions(0, 0, 0, 7).ignoreEmptyDir(true).build();
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(callback);
        assertThat(checks()).contains("/data/private/empty:read", "/data/private/empty:write", "/data/private/empty:execute");
        requests.clear();
        events.clear();
        assertThatThrownBy(() -> authorizer(snapshot(7, "hdfs", "/data/private/secret", "write")::decide, true).authorize(callback))
                .isInstanceOf(IOException.class).hasMessageContaining("/data/private/secret");
        assertThat(requestedSnapshot).hasValue(19);
        assertThat(checks()).contains("/data/private/secret:read", "/data/private/secret:write");
    }

    @Test
    void rejectsOversizeTreesBeforeMaterializingTheirLazyChildren()
    {
        List<HdfsNode> tooMany = new AbstractList<>()
        {
            @Override
            public HdfsNode get(int index)
            {
                throw new AssertionError("the discovery bound must be checked before child wrappers are allocated");
            }

            @Override
            public int size()
            {
                return 100_001;
            }
        };
        HdfsAuthorizationContext callback = context(0).nodes(new HdfsNode[] {node("", true, List.of()), node("data", true, List.of()),
                node("tree", true, tooMany)}).actions(0, 0, 0, 7).build();
        assertThatThrownBy(() -> authorizer(snapshot(7, "hdfs", null, "read")::decide, true).authorize(callback))
                .isInstanceOf(IOException.class).hasMessageContaining("subtree exceeds 100000");
        assertThat(requests).isEmpty();
    }

    @Test
    void snapshotsCheckExplicitAndLivePathsAndDoNotBypassExactFileDenies() throws IOException
    {
        HdfsAuthorizationContext callback = context(4).nodes(new HdfsNode[] {node("", true, List.of()), node("data", true, List.of()),
                node("data", true, List.of()), node("file", false, List.of())}).components(components("", "data", ".snapshot/s1", "file"))
                .path("/data/.snapshot/s1/file").ancestorIndex(2).build();
        assertThatThrownBy(() -> authorizer(snapshot(7, "hdfs", "/data/file", "read")::decide, false).authorize(callback))
                .isInstanceOf(IOException.class).hasMessageContaining("/data/file");
        assertThat(checks()).contains("/data/.snapshot/s1/file:read", "/data/file:read");
        requests.clear();
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(callback);
        assertThat(checks()).contains("/data/.snapshot/s1:execute", "/data:execute");
    }

    @Test
    void capturesOnlyOneSessionWhilePoliciesRefreshDuringTheCallback()
    {
        Snapshot first = snapshot(7, "hdfs", "/data/file", "read");
        Snapshot second = snapshot(8, "hdfs", "/", "execute");
        AtomicReference<Snapshot> current = new AtomicReference<>(first);
        AtomicInteger captures = new AtomicInteger();
        HdfsAuthorizer overlay = new HdfsAuthorizer(() -> {
            captures.incrementAndGet();
            Snapshot captured = requireNonNull(current.get());
            return request -> {
                current.set(second);
                return captured.decide(request);
            };
        }, events::add, () -> false, () -> HdfsMetrics.NONE);
        assertThatThrownBy(() -> overlay.authorize(context(4).build())).isInstanceOf(IOException.class).hasMessageContaining("read");
        assertThat(captures).hasValue(1);
        assertThat(field(events.get(0), "policyVersion")).isEqualTo(7L);
    }

    @Test
    void policyFailuresFailClosedButMetricsAndAuditFailuresCannotChangePermissions() throws IOException
    {
        HdfsAuthorizer broken = new HdfsAuthorizer(() -> {
            throw new IllegalStateException("engine unavailable");
        }, events::add,
                () -> true, () -> HdfsMetrics.NONE);
        assertThatThrownBy(() -> broken.authorize(context(4).build())).isInstanceOf(IOException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(field(events.get(0), "byGrantForge")).isEqualTo(true);
        Snapshot allowed = snapshot(7, "hdfs", null, "read");
        HdfsAuthorizer monitoringFailure = new HdfsAuthorizer(() -> allowed::decide, event -> {
            throw new IllegalStateException("queue unavailable");
        }, () -> false, () -> {
            throw new IllegalStateException("monitor unavailable");
        });
        monitoringFailure.authorize(context(4).build());
    }

    @Test
    void superuserChecksUseTheSuppliedActionsWhilePathlessAdminRemainsNative() throws IOException
    {
        HdfsAuthorizer overlay = authorizer(snapshot(7, "hdfs", "/data/file", "write")::decide, false);
        overlay.authorizeSuperuser(context(0).build(), 4);
        assertThat(checks()).containsExactly("/data/file:read");
        assertThatThrownBy(() -> overlay.authorizeSuperuser(context(0).build(), 2)).isInstanceOf(IOException.class);
        requests.clear();
        overlay.authorizeSuperuser(HdfsAuthorizationContext.builder("alice").build(), 7);
        assertThat(requests).isEmpty();
        assertThat(field(events.get(events.size() - 1), "byGrantForge")).isEqualTo(false);
        assertThatThrownBy(() -> overlay.authorizeSuperuser(context(0).build(), 8)).isInstanceOf(IOException.class);
    }

    @Test
    void truncatesOnlyAuditResourcesAndRetainsTheirLengthAndDigest() throws IOException
    {
        String path = "/" + "a".repeat(1200);
        authorizer(snapshot(7, "hdfs", null, "read")::decide, false).authorize(context(4).path(path).build());
        assertThat(requests.get(requests.size() - 1).resource().get("path")).isEqualTo(path);
        assertThat((String) field(events.get(events.size() - 1), "resource")).hasSizeLessThanOrEqualTo(1000).endsWith("...[truncated]");
        assertThat((String) field(events.get(events.size() - 1), "request")).contains("resourceLength=1201", "resourceSha256=");
    }
}
