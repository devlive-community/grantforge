// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

/** The plugin against Elasticsearch 9.4.8. */
class Elasticsearch94PluginIT
        extends ElasticsearchPluginContract
{
    @Override
    String version()
    {
        return "9.4.8";
    }
}
