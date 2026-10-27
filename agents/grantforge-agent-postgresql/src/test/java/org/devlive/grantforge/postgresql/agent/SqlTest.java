// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlTest
{
    @Test
    void quotesNamesAndText()
    {
        assertThat(Sql.identifier("orders")).isEqualTo("\"orders\"");
        assertThat(Sql.identifier("Order \"Items\"")).isEqualTo("\"Order \"\"Items\"\"\"");
        assertThat(Sql.identifiers(List.of("id", "it's"))).isEqualTo("\"id\", \"it's\"");
        assertThat(Sql.literal("it's \\ here")).isEqualTo("'it''s \\ here'");
        assertThatIllegalArgumentException().isThrownBy(() -> Sql.identifier("nul\0"));
        assertThatIllegalArgumentException().isThrownBy(() -> Sql.literal("nul\0"));
    }

    @Test
    void refusesTextsOfMoreThanOneStatement()
    {
        assertThatNoException().isThrownBy(() -> Sql.single("CREATE POLICY p ON t USING (a = ';' AND b = $$;$$) -- ; DROP TABLE t", false));
        assertThatNoException().isThrownBy(() -> Sql.single("SELECT 1 WHERE ? = ';'", true));
        assertThatNoException().isThrownBy(() -> Sql.single("SELECT 1;", false));
        assertThatThrownBy(() -> Sql.single("CREATE POLICY p ON t USING (true); DROP TABLE t; --)", false)).isInstanceOfSatisfying(SQLException.class,
                refused -> assertThat(refused.getSQLState()).isEqualTo("42601")).hasMessageContaining("one statement at a time");
    }
}
