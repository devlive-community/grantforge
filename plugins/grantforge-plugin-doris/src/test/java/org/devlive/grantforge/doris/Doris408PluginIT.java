// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

/** The Doris plugin against Apache Doris 4.0.8. */
class Doris408PluginIT
        extends DorisPluginContract
{
    @Override
    String version()
    {
        return "4.0.8";
    }
}
