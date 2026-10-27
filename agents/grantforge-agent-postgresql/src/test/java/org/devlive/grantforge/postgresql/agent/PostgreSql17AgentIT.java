// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

/** The agent against PostgreSQL 17.11. */
class PostgreSql17AgentIT
        extends PostgreSqlAgentContract
{
    @Override
    String version()
    {
        return "17.11";
    }
}
