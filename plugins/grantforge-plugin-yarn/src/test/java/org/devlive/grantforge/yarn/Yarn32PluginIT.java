// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

/** The YARN plugin against the ResourceManager of Hadoop 3.2.4. */
class Yarn32PluginIT
        extends YarnPluginContract
{
    @Override
    String version()
    {
        return "3.2.4";
    }
}
