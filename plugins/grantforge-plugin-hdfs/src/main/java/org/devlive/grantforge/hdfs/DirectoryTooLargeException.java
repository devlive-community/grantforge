// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import java.io.IOException;

/** A directory has more entries than the service may read in one lookup ({@code lookup.max.entries}). */
public final class DirectoryTooLargeException
        extends IOException
{
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param maximum the scan limit that was reached
     */
    public DirectoryTooLargeException(int maximum)
    {
        super("directory exceeds " + maximum + " entries; narrow the lookup directory using " + HdfsProvider.LOOKUP_ROOT);
    }
}
