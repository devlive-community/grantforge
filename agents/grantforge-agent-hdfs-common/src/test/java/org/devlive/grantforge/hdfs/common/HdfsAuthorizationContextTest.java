// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsAuthorizationContextTest
{
    @Test
    void copiesMutableDataAndRetainsAllNativeCallbackFields()
    {
        String[] groups = {"analysts"};
        HdfsNode[] nodes = {HdfsAuthorizerTest.node("", true, List.of()), null};
        byte[][] components = HdfsAuthorizerTest.components("", "data");
        HdfsAuthorizationContext context = HdfsAuthorizationContext.builder("alice", groups).nodes(nodes).components(components)
                .path("/data").snapshotId(19).ancestorIndex(0).checkOwner(true).ignoreEmptyDir(true).actions(4, 2, 1, 7)
                .operation("open").callerContext("trace").clientIp("10.2.3.4").build();
        groups[0] = "mutated";
        nodes[0] = null;
        components[1][0] = 'x';
        context.groups()[0] = "mutated-getter";
        context.nodes()[0] = null;
        requireNonNull(context.components()[1])[0] = 'x';
        assertThat(context.user()).isEqualTo("alice");
        assertThat(context.groups()).containsExactly("analysts");
        assertThat(context.nodes()[0]).isNotNull();
        assertThat(new String(requireNonNull(context.components()[1]), StandardCharsets.UTF_8)).isEqualTo("data");
        assertThat(context.path()).isEqualTo("/data");
        assertThat(context.snapshotId()).isEqualTo(19);
        assertThat(context.ancestorIndex()).isZero();
        assertThat(context.checkOwner()).isTrue();
        assertThat(context.ignoreEmptyDir()).isTrue();
        assertThat(context.ancestorAccess()).isEqualTo(4);
        assertThat(context.parentAccess()).isEqualTo(2);
        assertThat(context.access()).isEqualTo(1);
        assertThat(context.subAccess()).isEqualTo(7);
        assertThat(context.operation()).isEqualTo("open");
        assertThat(context.callerContext()).isEqualTo("trace");
        assertThat(context.clientIp()).isEqualTo("10.2.3.4");
    }

    @Test
    void rebuildsSingleInodeSnapshotAndRootPathsWithoutMutatingTheirComponents()
    {
        HdfsAuthorizationContext single = HdfsAuthorizationContext.builder("alice")
                .nodes(new HdfsNode[] {HdfsAuthorizerTest.node("file", false, List.of())})
                .components(HdfsAuthorizerTest.components("", "data", ".snapshot/s1", "file")).build();
        assertThat(single.inodePath(0)).isEqualTo("/data/.snapshot/s1/file");
        HdfsAuthorizationContext root = HdfsAuthorizationContext.builder("alice").nodes(new HdfsNode[] {null})
                .components(new byte[][] {null}).build();
        assertThat(root.inodePath(0)).isEqualTo("/");
        assertThat(root.components()[0]).isNull();
    }

    @Test
    void rejectsBlankUsersAndOutOfRangeNativeMasks()
    {
        assertThatThrownBy(() -> HdfsAuthorizationContext.builder(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdfsAuthorizationContext.builder("alice").actions(8, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdfsAuthorizationContext.builder("alice").actions(0, 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
