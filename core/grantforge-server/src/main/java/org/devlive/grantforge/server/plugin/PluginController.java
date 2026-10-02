// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.plugin;

import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.plugin.host.PluginService;
import org.devlive.grantforge.server.security.SessionUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** The installed service type plugins: which are in use, switching them on or off, and looking for new ones. */
@RestController
public final class PluginController
{
    private final PluginService plugins;

    /**
     * Creates the controller.
     *
     * @param plugins the installed plugins
     */
    public PluginController(PluginService plugins)
    {
        this.plugins = requireNonNull(plugins, "plugins");
    }

    /**
     * Lists the installed plugins.
     *
     * @return built-in plugins first, then by id
     */
    @RequirePermission("platform.plugin.read")
    @GetMapping("/api/v1/plugins")
    public List<PluginResponse> list()
    {
        return plugins.list().stream().map(PluginResponse::from).toList();
    }

    /**
     * Switches a plugin on.
     *
     * @param user the session's principal
     * @param id the plugin
     * @return the plugin afterwards
     */
    @RequirePermission("platform.plugin.update")
    @PostMapping("/api/v1/plugins/{id}/enable")
    public PluginResponse enable(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return PluginResponse.from(plugins.setEnabled(user.accountId(), id, true));
    }

    /**
     * Switches a plugin off.
     *
     * @param user the session's principal
     * @param id the plugin
     * @return the plugin afterwards
     */
    @RequirePermission("platform.plugin.update")
    @PostMapping("/api/v1/plugins/{id}/disable")
    public PluginResponse disable(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return PluginResponse.from(plugins.setEnabled(user.accountId(), id, false));
    }

    /**
     * Looks the plugins up again, for example after one was copied into the plugins directory.
     *
     * @param user the session's principal
     * @return the plugins found
     */
    @RequirePermission("platform.plugin.update")
    @PostMapping("/api/v1/plugins/rescan")
    public List<PluginResponse> rescan(@AuthenticationPrincipal SessionUser user)
    {
        return plugins.rescan(user.accountId()).stream().map(PluginResponse::from).toList();
    }
}
