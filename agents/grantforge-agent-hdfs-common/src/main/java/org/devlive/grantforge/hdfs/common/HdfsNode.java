// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.common;

import java.util.List;

/** An adapter's view of an inode, used only while the NameNode holds its authorization lock. */
public interface HdfsNode
{
    /**
     * Returns whether this inode is a directory.
     *
     * @return whether it is a directory
     */
    boolean isDirectory();

    /**
     * Returns the inode's local UTF-8-decoded name, excluding parent components.
     *
     * @return the local name
     */
    String name();

    /**
     * Returns the children at the requested snapshot. A lazy list view keeps discovery bounded before wrapping children.
     *
     * @param snapshotId Hadoop's snapshot identifier
     * @return the stable children under the NameNode's lock; empty for a file
     */
    List<HdfsNode> children(int snapshotId);
}
