// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

/** The ClickHouse agent against ClickHouse 24.8.14.39. */
class ClickHouse248AgentIT
        extends ClickHouseAgentContract
{
    @Override
    String version()
    {
        return "24.8.14.39";
    }
}
