// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

/** The Doris plugin against Apache Doris 2.1.11. */
class Doris2111PluginIT
        extends DorisPluginContract
{
    @Override
    String version()
    {
        return "2.1.11";
    }
}
