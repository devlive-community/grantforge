// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

/** The OpenSearch plugin against OpenSearch 3.3.2. */
class OpenSearch33PluginIT
        extends OpenSearchPluginContract
{
    @Override
    String version()
    {
        return "3.3.2";
    }
}
