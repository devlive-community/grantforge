// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

/** Which plugins an administrator switched off; plugins are on unless switched off. */
public interface PluginSwitches
{
    /**
     * Returns whether a plugin is on.
     *
     * @param pluginId the plugin
     * @return {@code false} if an administrator switched it off
     */
    boolean enabled(String pluginId);

    /**
     * Switches a plugin on or off.
     *
     * @param pluginId the plugin
     * @param enabled whether it is on
     */
    void set(String pluginId, boolean enabled);
}
