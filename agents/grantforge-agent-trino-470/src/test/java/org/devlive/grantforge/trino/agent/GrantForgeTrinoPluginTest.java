// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.Plugin;
import io.trino.spi.security.SystemAccessControlFactory;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class GrantForgeTrinoPluginTest
{
    @Test
    void offersTheGrantForgeAccessControl()
    {
        assertThat(new GrantForgeTrinoPlugin().getSystemAccessControlFactories()).singleElement()
                .extracting(SystemAccessControlFactory::getName).isEqualTo("grantforge");
    }

    @Test
    void isFoundAsTrinoFindsPlugins()
    {
        assertThat(ServiceLoader.load(Plugin.class)).anySatisfy(plugin -> assertThat(plugin).isInstanceOf(GrantForgeTrinoPlugin.class));
    }
}
