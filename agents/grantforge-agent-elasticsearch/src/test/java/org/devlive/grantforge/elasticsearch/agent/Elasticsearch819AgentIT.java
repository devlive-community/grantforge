// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

/** The agent against Elasticsearch 8.19.23. */
class Elasticsearch819AgentIT
        extends ElasticsearchAgentContract
{
    @Override
    String version()
    {
        return "8.19.23";
    }
}
