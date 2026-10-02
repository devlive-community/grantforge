// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCommandTest
{
    @Test
    void copiesTheValues()
    {
        Map<String, String> values = new HashMap<>(Map.of("url", "x"));
        ServiceCommand command = new ServiceCommand("hive", "Hive", null, true, values);
        values.clear();
        assertThat(command.values()).containsEntry("url", "x");
    }
}
