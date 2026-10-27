// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

/** The Doris agent against Apache Doris 4.1.5. */
class Doris415AgentIT
        extends DorisAgentContract
{
    @Override
    String version()
    {
        return "4.1.5";
    }
}
