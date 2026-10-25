// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

/** The ClickHouse plugin against ClickHouse 24.8.14.39. */
class ClickHouse248PluginIT
        extends ClickHousePluginContract
{
    @Override
    String version()
    {
        return "24.8.14.39";
    }
}
