// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

/** The OpenSearch agent against OpenSearch 3.9.0. */
class OpenSearch39AgentIT
        extends OpenSearchAgentContract
{
    @Override
    String version()
    {
        return "3.9.0";
    }
}
