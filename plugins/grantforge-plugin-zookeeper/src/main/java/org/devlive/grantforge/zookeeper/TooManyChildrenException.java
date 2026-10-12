// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import java.io.IOException;

/** A znode has more children than a service lets a lookup or browse read. */
final class TooManyChildrenException
        extends IOException
{
    private static final long serialVersionUID = 1L;

    TooManyChildrenException(String path, int children, int limit)
    {
        super(path + " has " + children + " children, more than the " + limit + " this service reads at once");
    }
}
