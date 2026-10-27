// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

/** The Doris agent against Apache Doris 4.0.8. */
class Doris408AgentIT
        extends DorisAgentContract
{
    @Override
    String version()
    {
        return "4.0.8";
    }
}
