// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import org.junit.jupiter.api.Test;

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

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void unknownEnginesAreRejected()
    {
        assertThatThrownBy(() -> TestDatabase.start("db2:11")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown database 'db2:11'");
        assertThatThrownBy(() -> TestDatabase.start(null)).isInstanceOf(NullPointerException.class);
    }
}
