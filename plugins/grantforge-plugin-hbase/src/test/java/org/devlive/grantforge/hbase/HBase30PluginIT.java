// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

/** The HBase plugin against HBase 3.0.0. */
class HBase30PluginIT
        extends HBasePluginContract
{
    @Override
    String version()
    {
        return "3.0.0";
    }
}
