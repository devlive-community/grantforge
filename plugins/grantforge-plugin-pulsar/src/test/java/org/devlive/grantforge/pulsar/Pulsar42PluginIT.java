// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

/** The Pulsar plugin against a broker of Pulsar 4.2.5. */
class Pulsar42PluginIT
        extends PulsarPluginContract
{
    @Override
    String version()
    {
        return "4.2.5";
    }
}
