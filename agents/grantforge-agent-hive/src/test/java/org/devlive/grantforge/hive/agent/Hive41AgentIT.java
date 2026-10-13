// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

/** The agent inside HiveServer2 4.1.0. */
class Hive41AgentIT
        extends HiveAgentContract
{
    @Override
    String image()
    {
        return "apache/hive:4.1.0";
    }
}
