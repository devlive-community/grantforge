// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

/** The agent jar for Trino 479 and later in Trino 479. */
class Trino479AgentIT
        extends TrinoAgentContract
{
    @Override
    String version()
    {
        return "479";
    }
}
