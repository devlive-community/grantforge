// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.server.namenode.INodeDirectory;
import org.apache.hadoop.hdfs.util.ReadOnlyList;
import org.devlive.grantforge.hdfs.common.HdfsNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HdfsNativeNodeTest
{
    @Test
    void presentsNativeNamesAndTreatsFilesAsLeaves()
    {
        INode file = mock(INode.class);
        when(file.getLocalNameBytes()).thenReturn("订单".getBytes(StandardCharsets.UTF_8));
        HdfsNode node = new HdfsNativeNode(file);

        assertThat(node.name()).isEqualTo("订单");
        assertThat(node.isDirectory()).isFalse();
        assertThat(node.children(19)).isEmpty();
        verify(file, never()).asDirectory();
    }

    @Test
    void obtainsSnapshotChildrenLazilyBeforeTheCommonDiscoveryLimitRuns()
    {
        INodeDirectory directory = mock(INodeDirectory.class);
        when(directory.isDirectory()).thenReturn(true);
        when(directory.asDirectory()).thenReturn(directory);
        INode child = mock(INode.class);
        when(child.getLocalNameBytes()).thenReturn("child".getBytes(StandardCharsets.UTF_8));
        when(directory.getChildrenList(19)).thenReturn(ReadOnlyList.Util.asReadOnlyList(Collections.nCopies(1_000_001, child)));

        List<HdfsNode> children = new HdfsNativeNode(directory).children(19);

        assertThat(children.size()).isEqualTo(1_000_001);
        verify(directory).getChildrenList(19);
        verify(child, never()).getLocalNameBytes();
        assertThat(children.get(0).name()).isEqualTo("child");
    }
}
