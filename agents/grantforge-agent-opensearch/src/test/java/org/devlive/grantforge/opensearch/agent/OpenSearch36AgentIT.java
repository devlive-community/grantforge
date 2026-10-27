// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

/** The OpenSearch agent against OpenSearch 3.6.0. */
class OpenSearch36AgentIT
        extends OpenSearchAgentContract
{
    @Override
    String version()
    {
        return "3.6.0";
    }
}
