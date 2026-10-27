// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server;

import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
    @ExtendWith(OutputCaptureExtension.class)
    void bannerNamesGrantForgeAndItsVersion(CapturedOutput output)
    {
        try (ConfigurableApplicationContext context = GrantForge.start("--spring.main.web-application-type=none")) {
            String version = context.getEnvironment().getProperty(GrantForge.VERSION_PROPERTY);
            // The build filters the version file, so the version is the project's, never the fallback.
            assertThat(version).matches("\\d+\\.\\d+\\.\\d+.*");
            assertThat(output.getOut())
                    .contains(":: GrantForge " + version + " ::")
                    .contains("Java " + System.getProperty("java.version"))
                    .doesNotContain(":: Spring Boot ::");
        }
    }

    @Test
    void versionIsUnknownWithoutTheVersionFile()
    {
        assertThat(GrantForge.version("/META-INF/grantforge/missing.properties")).isEqualTo("unknown");
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
