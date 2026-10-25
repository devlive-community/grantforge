// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

/** The ClickHouse plugin against ClickHouse 26.3.60.2. */
class ClickHouse263PluginIT
        extends ClickHousePluginContract
{
    @Override
    String version()
    {
        return "26.3.60.2";
    }
}
