// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.plugin;

import org.devlive.grantforge.plugin.host.InstalledPlugin;
import org.devlive.grantforge.plugin.host.PluginSource;
import org.devlive.grantforge.plugin.host.PluginStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginResponseTest
{
    @Test
    void summarisesEachServiceType()
    {
        PluginResponse response = PluginResponse.from(new InstalledPlugin("builtin-demo", "1.0.0", "Demo", null, "1.0.0",
                PluginSource.BUILTIN, "Demo", PluginStatus.ACTIVE, null, List.of(new DemoServiceTypeProvider().definition())));

        assertThat(response.id()).isEqualTo("builtin-demo");
        assertThat(response.serviceTypes()).singleElement().satisfies(type -> {
            assertThat(type.name()).isEqualTo("demo");
            assertThat(type.resources()).containsExactly("database", "table");
            assertThat(type.accessTypes()).containsExactly("select", "update");
            assertThat(type.dataMask()).isFalse();
            assertThat(type.rowFilter()).isFalse();
            assertThat(type.version()).isEqualTo(1);
        });
    }
}
