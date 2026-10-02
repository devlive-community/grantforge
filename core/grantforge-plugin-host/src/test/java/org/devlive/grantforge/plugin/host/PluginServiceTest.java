// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginServiceTest
{
    private static InstalledPlugin plugin(String id, PluginStatus status)
    {
        return new InstalledPlugin(id, "1.0.0", id, null, "1.0.0", PluginSource.EXTERNAL, id, status, null, List.of());
    }

    @Test
    void listsSwitchesAndRescansPluginsAndAuditsTheChanges()
    {
        PluginRegistry registry = mock(PluginRegistry.class);
        AuditLog audit = mock(AuditLog.class);
        PluginService service = new PluginService(registry, audit);
        when(registry.plugins()).thenReturn(List.of(plugin("hdfs", PluginStatus.ACTIVE)));
        when(registry.setEnabled("hdfs", false)).thenReturn(plugin("hdfs", PluginStatus.DISABLED));
        when(registry.scan()).thenReturn(List.of(plugin("hdfs", PluginStatus.ACTIVE), plugin("hive", PluginStatus.FAILED)));
        when(registry.setEnabled("nothing", true)).thenThrow(new IllegalArgumentException("no plugin nothing"));

        assertThat(service.list()).extracting(InstalledPlugin::id).containsExactly("hdfs");
        TenantContext.runInTenant(1, () -> {
            assertThat(service.setEnabled(7, "hdfs", false).status()).isEqualTo(PluginStatus.DISABLED);
            assertThat(service.rescan(7)).hasSize(2);
            assertThatThrownBy(() -> service.setEnabled(7, "nothing", true)).isInstanceOfSatisfying(GrantForgeException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        });
        ArgumentCaptor<AuditRecord> records = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit, times(2)).record(records.capture());
        assertThat(records.getAllValues()).extracting(AuditRecord::action, AuditRecord::reason)
                .containsExactly(tuple(AuditAction.PLUGIN_DISABLED, "DISABLED"),
                        tuple(AuditAction.PLUGINS_RESCANNED, "1"));
    }
}
