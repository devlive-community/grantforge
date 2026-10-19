// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

/** The Trino plugin against Trino 476. */
class Trino476PluginIT
        extends TrinoPluginContract
{
    @Override
    String version()
    {
        return "476";
    }
}
