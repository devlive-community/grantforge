// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

/** The agent against PostgreSQL 16.15. */
class PostgreSql16AgentIT
        extends PostgreSqlAgentContract
{
    @Override
    String version()
    {
        return "16.15";
    }
}
