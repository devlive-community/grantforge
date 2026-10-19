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

class GrantForgeTrino479PluginTest
{
    @Test
    void offersTheGrantForgeAccessControlOfTheLaterLines()
    {
        assertThat(new GrantForgeTrino479Plugin().getSystemAccessControlFactories()).singleElement()
                .extracting(SystemAccessControlFactory::getName).isEqualTo("grantforge");
    }

    @Test
    void isTheOnePluginTrinoFinds()
    {
        assertThat(ServiceLoader.load(Plugin.class)).singleElement().isInstanceOf(GrantForgeTrino479Plugin.class);
    }
}
