// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

/** The ClickHouse plugin against ClickHouse 25.8.33.6. */
class ClickHouse258PluginIT
        extends ClickHousePluginContract
{
    @Override
    String version()
    {
        return "25.8.33.6";
    }
}
