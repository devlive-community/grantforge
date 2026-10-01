// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseTest
{
    @Test
    void h2RunsInMemoryWithoutDocker()
    {
        try (TestDatabase database = TestDatabase.start(" H2 ")) {
            assertThat(database.url()).startsWith("jdbc:h2:mem:");
            assertThat(database.username()).isEqualTo("sa");
            assertThat(database.password()).isEmpty();
            assertThat(database).hasToString(" H2 ");
        }
    }

    @Test
    void systemPropertyDefaultsToH2()
    {
        String previous = System.clearProperty("grantforge.it.database");
        try (TestDatabase database = TestDatabase.fromSystemProperty()) {
            assertThat(database.url()).startsWith("jdbc:h2:mem:");
        }
        finally {
            if (previous != null) {
                System.setProperty("grantforge.it.database", previous);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "postgres:17, org.testcontainers.postgresql.PostgreSQLContainer",
            "mysql:8.4, org.testcontainers.mysql.MySQLContainer",
            "MariaDB:11.4, org.testcontainers.mariadb.MariaDBContainer",
            "oracle:23, org.testcontainers.oracle.OracleContainer",
            "sqlserver:2022, org.testcontainers.mssqlserver.MSSQLServerContainer",
    })
    void enginesMapToTheirContainers(String spec, Class<?> type)
    {
        // Creating a container neither contacts Docker nor pulls the image; only start() does.
        assertThat(TestDatabase.container(spec)).isExactlyInstanceOf(type);
        assertThat(TestDatabase.container("h2")).isNull();
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void unknownEnginesAreRejected()
    {
        assertThatThrownBy(() -> TestDatabase.start("db2:11")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown database 'db2:11'");
        assertThatThrownBy(() -> TestDatabase.start(null)).isInstanceOf(NullPointerException.class);
    }
}
