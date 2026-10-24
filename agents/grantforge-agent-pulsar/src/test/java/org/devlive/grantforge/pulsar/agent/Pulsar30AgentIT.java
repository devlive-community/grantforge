// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar.agent;

/** The agent in a broker of Pulsar 3.0.17. */
class Pulsar30AgentIT
        extends PulsarAgentContract
{
    @Override
    String version()
    {
        return "3.0.17";
    }
}
