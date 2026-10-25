// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

/** The ClickHouse plugin against ClickHouse 26.8.34.13. */
class ClickHouse268PluginIT
        extends ClickHousePluginContract
{
    @Override
    String version()
    {
        return "26.8.34.13";
    }
}
