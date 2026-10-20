// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

/** The agent jar in the ResourceManager of Hadoop 3.4.3. */
class Yarn34AgentIT
        extends YarnAgentContract
{
    @Override
    String version()
    {
        return "3.4.3";
    }
}
