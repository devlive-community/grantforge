// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

/** Where a plugin comes from. */
public enum PluginSource
{
    /** Shipped with the server and found on its classpath. */
    BUILTIN,

    /** Installed into the plugins directory. */
    EXTERNAL
}
