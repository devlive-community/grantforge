// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LegacyImportConfigurationTest
{
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withBean(LegacyImporter.class, () -> mock(LegacyImporter.class))
            .withUserConfiguration(LegacyImportConfiguration.class);

    @Test
    void runsOnlyWhenASourceIsGiven()
    {
        contexts.run(context -> assertThat(context).doesNotHaveBean(LegacyImportRunner.class));
        contexts.withPropertyValues("grantforge.legacy.source-url=jdbc:h2:mem:old", "grantforge.legacy.tenant=acme")
                .run(context -> assertThat(context).hasSingleBean(LegacyImportRunner.class)
                        .getBean(LegacyImportProperties.class).extracting(LegacyImportProperties::tenant).isEqualTo("acme"));
    }
}
