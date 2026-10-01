// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void databaseIsMigratedAndSessionsDoNotSpanRequests() throws Exception
    {
        try (ConfigurableApplicationContext context = GrantForge.start("--server.port=0")) {
            assertThat(context.getEnvironment().getProperty("spring.jpa.open-in-view")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            // Repositories of other modules are found from the shared base package.
            assertThat(context.getBean(UserAccountRepository.class).count()).isZero();
            try (Connection connection = context.getBean(DataSource.class).getConnection()) {
                assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:grantforge");
                // Module changelogs are applied (and Hibernate validated them against the entities).
                try (ResultSet tables = connection.getMetaData().getTables(null, null, "GF_USER_ACCOUNT", null)) {
                    assertThat(tables.next()).isTrue();
                }
            }
        }
    }

    @Test
    void mainStartsWithoutErrors()
    {
        // main() cannot return its context; run it without a web server so nothing binds a port.
        // Spring's shutdown hook closes the context when the test JVM exits.
        assertThatCode(() -> GrantForge.main(new String[] {"--spring.main.web-application-type=none"}))
                .doesNotThrowAnyException();
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
