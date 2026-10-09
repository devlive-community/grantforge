// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import java.io.IOException;

/** The lookup user could not sign in, before the cluster was asked anything. */
final class HdfsLoginException
        extends IOException
{
    private static final long serialVersionUID = 1L;

    HdfsLoginException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
