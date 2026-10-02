// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginClassLoaderTest
{
    @Test
    void sharesThePluginApiAndTheJdkButNothingElseOfTheServer()
            throws IOException, ClassNotFoundException
    {
        try (PluginClassLoader loader = new PluginClassLoader("demo", List.of(), PluginClassLoaderTest.class.getClassLoader())) {
            assertThat(loader.pluginId()).isEqualTo("demo");
            assertThat(loader.getName()).isEqualTo("plugin-demo");
            assertThat(loader.loadClass(ServiceTypeProvider.class.getName())).isSameAs(ServiceTypeProvider.class);
            assertThat(loader.loadClass(String.class.getName())).isSameAs(String.class);
            assertThatThrownBy(() -> loader.loadClass(PluginRegistry.class.getName())).isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> loader.loadClass("org.springframework.context.ApplicationContext"))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }
}
