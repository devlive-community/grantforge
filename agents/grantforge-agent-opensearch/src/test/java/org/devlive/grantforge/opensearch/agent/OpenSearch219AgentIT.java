// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

/** The OpenSearch agent against OpenSearch 2.19.7. */
class OpenSearch219AgentIT
        extends OpenSearchAgentContract
{
    @Override
    String version()
    {
        return "2.19.7";
    }
}
