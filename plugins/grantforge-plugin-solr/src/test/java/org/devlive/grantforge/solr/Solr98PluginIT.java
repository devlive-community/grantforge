// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

/** The Solr plugin against a SolrCloud node of Solr 9.8.1. */
class Solr98PluginIT
        extends SolrPluginContract
{
    @Override
    String version()
    {
        return "9.8.1";
    }
}
