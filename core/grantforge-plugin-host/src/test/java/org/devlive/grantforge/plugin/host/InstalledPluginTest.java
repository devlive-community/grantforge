// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstalledPluginTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsServiceTypesAndNeedsItsValues()
    {
        List<ServiceTypeDefinition> types = new ArrayList<>();
        InstalledPlugin plugin = new InstalledPlugin("hdfs", "1.0.0", "HDFS", null, "1.0.0", PluginSource.EXTERNAL, "hdfs.jar",
                PluginStatus.DISABLED, null, types);
        assertThat(plugin.serviceTypes()).isEmpty();
        assertThatThrownBy(() -> new InstalledPlugin("hdfs", null, "HDFS", null, null, PluginSource.EXTERNAL, "x", null, null,
                List.of())).isInstanceOf(NullPointerException.class);
        assertThat(PluginSource.values()).containsExactly(PluginSource.BUILTIN, PluginSource.EXTERNAL);
        assertThat(PluginStatus.valueOf("INCOMPATIBLE")).isEqualTo(PluginStatus.INCOMPATIBLE);
    }
}
