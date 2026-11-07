// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

/** The agent against MongoDB 6.0.27. */
class MongoDb60AgentIT
        extends MongoDbAgentContract
{
    @Override
    String version()
    {
        return "6.0.27";
    }
}
