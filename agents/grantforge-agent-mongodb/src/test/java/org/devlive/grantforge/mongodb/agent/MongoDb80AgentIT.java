// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

/** The agent against MongoDB 8.0.34. */
class MongoDb80AgentIT
        extends MongoDbAgentContract
{
    @Override
    String version()
    {
        return "8.0.34";
    }
}
