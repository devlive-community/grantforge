// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.hdfs.server.namenode.INode;
import org.apache.hadoop.hdfs.util.ReadOnlyList;
import org.devlive.grantforge.hdfs.common.HdfsNode;

import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

/** Presents a NameNode inode to the common authorizer without exposing Hadoop classes to it. */
final class HdfsNativeNode
        implements HdfsNode
{
    private final INode inode;

    HdfsNativeNode(INode inode)
    {
        this.inode = inode;
    }

    @Override
    public boolean isDirectory()
    {
        return inode.isDirectory();
    }

    @Override
    public String name()
    {
        return new String(inode.getLocalNameBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public List<HdfsNode> children(int snapshotId)
    {
        if (!inode.isDirectory()) {
            return Collections.emptyList();
        }
        ReadOnlyList<INode> children = inode.asDirectory().getChildrenList(snapshotId);
        // The common authorizer checks the discovery limit before visiting any child. Copying or eagerly wrapping
        // the whole list here would allocate without that bound while the NameNode's namespace lock is held.
        return new AbstractList<HdfsNode>()
        {
            @Override
            public HdfsNode get(int index)
            {
                return new HdfsNativeNode(children.get(index));
            }

            @Override
            public int size()
            {
                return children.size();
            }
        };
    }
}
