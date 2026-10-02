// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

/** Whether a plugin's service types are in use. */
public enum PluginStatus
{
    /** Loaded and in use. */
    ACTIVE,

    /** Switched off by an administrator; not loaded. */
    DISABLED,

    /** Built against a plugin API this server cannot run. */
    INCOMPATIBLE,

    /** Could not be read or loaded, or one of its providers failed; set aside with the reason. */
    FAILED
}
