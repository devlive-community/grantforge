// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/** Whether an administrator switched a plugin on or off; plugins without a row are on. */
@Entity
@Table(name = "gf_plugin_state")
public class PluginState
{
    @Id
    @Column(name = "plugin_id", nullable = false, updatable = false, length = 64)
    private String pluginId = "";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.EPOCH;

    /** For JPA. */
    protected PluginState()
    {
    }

    /**
     * Records the switch of a plugin.
     *
     * @param pluginId the plugin
     * @param enabled whether it is on
     * @param now when
     * @return the state
     */
    public static PluginState of(String pluginId, boolean enabled, Instant now)
    {
        PluginState state = new PluginState();
        state.pluginId = requireNonNull(pluginId, "pluginId");
        state.enabled = enabled;
        state.updatedAt = requireNonNull(now, "now");
        return state;
    }

    /**
     * Switches the plugin.
     *
     * @param on whether it is on
     * @param now when
     */
    public void set(boolean on, Instant now)
    {
        this.enabled = on;
        this.updatedAt = requireNonNull(now, "now");
    }

    /**
     * Returns the plugin.
     *
     * @return its id
     */
    public String getPluginId()
    {
        return pluginId;
    }

    /**
     * Returns whether the plugin is on.
     *
     * @return {@code false} if switched off
     */
    public boolean isEnabled()
    {
        return enabled;
    }
}
