// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTypeProviderTest
{
    /** A provider that only describes itself; registered in META-INF/services for the test. */
    public static final class MinimalProvider
            implements ServiceTypeProvider
    {
        @Override
        public ServiceTypeDefinition definition()
        {
            return ServiceTypeDefinition.builder("minimal").resources(ResourceDefinition.builder("item").build())
                    .accessTypes(AccessTypeDefinition.of("use", "Use")).build();
        }
    }

    private static final ServiceConfig CONFIG = new ServiceConfig("demo", Map.of());

    @Test
    void providersAreFoundThroughTheServiceLoader()
    {
        List<ServiceTypeProvider> providers = ServiceLoader.load(ServiceTypeProvider.class).stream()
                .map(ServiceLoader.Provider::get).toList();

        assertThat(providers).singleElement().satisfies(provider -> {
            assertThat(provider.definition().name()).isEqualTo("minimal");
            assertThat(provider.validateConfig(CONFIG)).isEmpty();
            assertThat(provider.testConnection(CONFIG)).isEqualTo(ConnectionResult.unsupported());
            assertThat(provider.lookup(new LookupRequest(CONFIG, "item", "", Map.of(), 10))).isEmpty();
            // A plugin that cannot browse says so as a lookup failure, which the console shows.
            assertThatThrownBy(() -> provider.browse(new BrowseRequest(CONFIG, "item", "", null, 10)))
                    .isInstanceOfSatisfying(LookupException.class, failure -> assertThat(failure.getReason())
                            .isEqualTo(LookupException.Reason.FAILED)).hasMessage("this plugin cannot browse item");
        });
    }
}
