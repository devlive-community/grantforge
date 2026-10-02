// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceViewTest
{
    @Test
    void isAvailableWhileAPluginProvidesTheType()
    {
        ServiceView view = new ServiceView(1, "hive", "Hive", null, "hive", "Hive", true, Map.of("url", "x"), Set.of("password"));
        assertThat(view.available()).isTrue();
        assertThat(new ServiceView(1, "hive", "Hive", null, "hive", null, true, Map.of(), Set.of()).available()).isFalse();
    }
}
