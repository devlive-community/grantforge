// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HdfsNodeTest
{
    @Test
    void borrowedInodeViewsExposeLocalNamesAndStableChildListsForAuthorization() throws IOException
    {
        HdfsNode file = HdfsAuthorizerTest.node("tést", false, List.of());
        HdfsNode directory = HdfsAuthorizerTest.node("data", true, List.of(file));
        assertThat(file.isDirectory()).isFalse();
        assertThat(file.name()).isEqualTo("tést");
        assertThat(file.children(19)).isEmpty();
        assertThat(directory.children(19)).containsExactly(file);
        HdfsAuthorizationContext context = HdfsAuthorizationContext.builder("alice").nodes(new HdfsNode[] {directory})
                .components(HdfsAuthorizerTest.components("", "data")).actions(0, 0, 0, 7).build();
        new HdfsAuthorizer(HdfsAuthorizerTest.snapshot(7, "hdfs", null, "read")::decide, event -> { }, false).authorize(context);
    }
}
