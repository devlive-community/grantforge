// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GrantForgeTest
{
    @Test
    void startRunsTheApplicationOnTheRequestedPort()
    {
        try (ConfigurableApplicationContext context = GrantForge.start("--server.port=0")) {
            assertThat(context.isRunning()).isTrue();
            assertThat(context.getEnvironment().getProperty("spring.application.name")).isEqualTo("grantforge");
            assertThat(context.getEnvironment().getProperty("local.server.port")).isNotBlank();
        }
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void startRejectsNullArguments()
    {
        assertThatNullPointerException()
                .isThrownBy(() -> GrantForge.start((String[]) null))
                .withMessage("args");
    }
}
