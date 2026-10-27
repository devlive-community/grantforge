// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

/** The agent against Elasticsearch 9.3.8. */
class Elasticsearch93AgentIT
        extends ElasticsearchAgentContract
{
    @Override
    String version()
    {
        return "9.3.8";
    }
}
