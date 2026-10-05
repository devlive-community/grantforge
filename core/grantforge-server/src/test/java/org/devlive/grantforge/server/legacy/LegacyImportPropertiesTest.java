// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyImportPropertiesTest
{
    private static LegacyImportProperties bind(Map<String, String> values)
    {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("grantforge.legacy", LegacyImportProperties.class);
    }

    @Test
    void defaultsToADryRunIntoTheLegacyApplication()
    {
        LegacyImportProperties properties = bind(Map.of("grantforge.legacy.source-url", "jdbc:mysql://db/authx"));

        assertThat(properties.sourceUrl()).isEqualTo("jdbc:mysql://db/authx");
        assertThat(properties.apply()).isFalse();
        assertThat(properties.application()).isEqualTo("legacy");
        assertThat(properties.report()).isEqualTo(Path.of("legacy-import-report.json"));
        assertThat(properties.tenant()).isNull();
    }

    @Test
    void treatsBlankValuesAsAbsent()
    {
        LegacyImportProperties properties = bind(Map.of("grantforge.legacy.source-url", " ", "grantforge.legacy.tenant", "",
                "grantforge.legacy.source-username", " ", "grantforge.legacy.apply", "true"));

        assertThat(properties.sourceUrl()).isNull();
        assertThat(properties.tenant()).isNull();
        assertThat(properties.sourceUsername()).isNull();
        assertThat(properties.apply()).isTrue();
    }
}
