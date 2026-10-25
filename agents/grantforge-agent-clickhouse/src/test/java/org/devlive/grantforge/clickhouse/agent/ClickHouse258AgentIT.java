// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

/** The ClickHouse agent against ClickHouse 25.8.33.6. */
class ClickHouse258AgentIT
        extends ClickHouseAgentContract
{
    @Override
    String version()
    {
        return "25.8.33.6";
    }
}
