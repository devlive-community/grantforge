// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

/** The Hive plugin against HiveServer2 4.1.0. */
class Hive41PluginIT
        extends HivePluginContract
{
    @Override
    String image()
    {
        return "apache/hive:4.1.0";
    }
}
