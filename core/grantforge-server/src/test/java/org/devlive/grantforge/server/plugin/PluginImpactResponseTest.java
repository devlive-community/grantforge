// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.plugin;

import org.devlive.grantforge.service.PluginImpact;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PluginImpactResponseTest
{
    @Test
    void convertsTheReportTheConsoleConfirmsWith()
    {
        PluginImpactResponse response = PluginImpactResponse.from(new PluginImpact.Report("builtin-demo", "Demo",
                List.of("demo"), List.of(affected())));

        assertThat(response.pluginId()).isEqualTo("builtin-demo");
        assertThat(response.pluginName()).isEqualTo("Demo");
        assertThat(response.serviceTypes()).containsExactly("demo");
        assertThat(response.services()).singleElement().satisfies(service -> {
            assertThat(service.tenantCode()).isEqualTo("acme");
            assertThat(service.tenantName()).isEqualTo("Acme");
            assertThat(service.id()).isEqualTo("7");
            assertThat(service.name()).isEqualTo("analytics");
            assertThat(service.label()).isEqualTo("Analytics");
            assertThat(service.serviceType()).isEqualTo("demo");
            assertThat(service.enabled()).isTrue();
            assertThat(service.policies()).isEqualTo(3);
            assertThat(service.agents()).isEqualTo(2);
        });
    }

    @Test
    void copiesTheListsSoNothingChangesUnderTheConsole()
    {
        List<String> serviceTypes = new ArrayList<>(List.of("demo"));
        List<PluginImpactResponse.AffectedService> services = new ArrayList<>(List.of(responseService()));
        PluginImpactResponse response = new PluginImpactResponse("builtin-demo", "Demo", serviceTypes, services);

        serviceTypes.add("other");
        services.add(new PluginImpactResponse.AffectedService("beta", "Beta", "8", "warehouse", "Warehouse", "demo", false, 0, 0));

        assertThat(response.serviceTypes()).containsExactly("demo");
        assertThat(response.services()).singleElement().satisfies(service -> assertThat(service.id()).isEqualTo("7"));
    }

    private static PluginImpact.AffectedService affected()
    {
        return new PluginImpact.AffectedService("acme", "Acme", 7, "analytics", "Analytics", "demo", true, 3, 2);
    }

    private static PluginImpactResponse.AffectedService responseService()
    {
        return new PluginImpactResponse.AffectedService("acme", "Acme", "7", "analytics", "Analytics", "demo", true, 3, 2);
    }
}
