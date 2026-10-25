// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

/** The ClickHouse agent against ClickHouse 26.3.60.2. */
class ClickHouse263AgentIT
        extends ClickHouseAgentContract
{
    @Override
    String version()
    {
        return "26.3.60.2";
    }
}
