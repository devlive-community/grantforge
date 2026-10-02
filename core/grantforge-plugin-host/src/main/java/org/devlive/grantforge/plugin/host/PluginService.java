// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** What administrators do with plugins: list them, switch them on or off, look them up again. Audited. */
@Service
public final class PluginService
{
    private final PluginRegistry registry;
    private final AuditLog audit;

    /**
     * Creates the service.
     *
     * @param registry the installed plugins
     * @param audit records every change
     */
    public PluginService(PluginRegistry registry, AuditLog audit)
    {
        this.registry = requireNonNull(registry, "registry");
        this.audit = requireNonNull(audit, "audit");
    }

    /**
     * Returns the installed plugins.
     *
     * @return built-in plugins first, then by id
     */
    public List<InstalledPlugin> list()
    {
        return registry.plugins();
    }

    /**
     * Switches a plugin on or off.
     *
     * @param actorId the account asking
     * @param pluginId the plugin
     * @param enabled whether it is on
     * @return the plugin afterwards
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown plugin
     */
    public InstalledPlugin setEnabled(long actorId, String pluginId, boolean enabled)
    {
        InstalledPlugin plugin;
        try {
            plugin = registry.setEnabled(pluginId, enabled);
        }
        catch (IllegalArgumentException unknown) {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, String.valueOf(unknown.getMessage()), unknown);
        }
        record(enabled ? AuditAction.PLUGIN_ENABLED : AuditAction.PLUGIN_DISABLED, actorId, pluginId, plugin.status().name());
        return plugin;
    }

    /**
     * Looks the plugins up again, for example after one was copied into the plugins directory.
     *
     * @param actorId the account asking
     * @return the plugins found
     */
    public List<InstalledPlugin> rescan(long actorId)
    {
        List<InstalledPlugin> plugins = registry.scan();
        record(AuditAction.PLUGINS_RESCANNED, actorId, null,
                Long.toString(plugins.stream().filter(plugin -> plugin.status() == PluginStatus.ACTIVE).count()));
        return plugins;
    }

    private void record(AuditAction action, long actorId, @Nullable String target, String reason)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null, target, reason));
    }
}
