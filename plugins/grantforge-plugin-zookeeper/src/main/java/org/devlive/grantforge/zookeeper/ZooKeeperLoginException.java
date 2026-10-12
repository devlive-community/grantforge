// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import java.io.IOException;

/** Signing in to an ensemble failed: the keytab cannot be read, or the ensemble refused the principal. */
final class ZooKeeperLoginException
        extends IOException
{
    private static final long serialVersionUID = 1L;

    ZooKeeperLoginException(String message)
    {
        super(message);
    }
}
