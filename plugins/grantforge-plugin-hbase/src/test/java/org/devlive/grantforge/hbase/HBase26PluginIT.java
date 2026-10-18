// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

/** The HBase plugin against HBase 2.6.7. */
class HBase26PluginIT
        extends HBasePluginContract
{
    @Override
    String version()
    {
        return "2.6.7";
    }
}
