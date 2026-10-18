// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

/** The agent inside HBase 3.0.0. */
class HBase30AgentIT
        extends HBaseAgentContract
{
    @Override
    String version()
    {
        return "3.0.0";
    }
}
