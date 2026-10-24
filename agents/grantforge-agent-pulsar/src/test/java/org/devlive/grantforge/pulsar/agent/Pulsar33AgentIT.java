// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

/** The agent in a broker of Pulsar 3.3.9. */
class Pulsar33AgentIT
        extends PulsarAgentContract
{
    @Override
    String version()
    {
        return "3.3.9";
    }
}
