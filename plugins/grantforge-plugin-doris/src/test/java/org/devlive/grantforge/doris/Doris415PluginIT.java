// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

/** The Doris plugin against Apache Doris 4.1.5. */
class Doris415PluginIT
        extends DorisPluginContract
{
    @Override
    String version()
    {
        return "4.1.5";
    }
}
