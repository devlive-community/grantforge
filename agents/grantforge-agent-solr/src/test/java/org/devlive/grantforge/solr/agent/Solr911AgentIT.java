// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr.agent;

/** The agent in a SolrCloud node of Solr 9.11.0. */
class Solr911AgentIT
        extends SolrAgentContract
{
    @Override
    String version()
    {
        return "9.11.0";
    }
}
