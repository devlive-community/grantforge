// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifiersTest
{
    @Test
    void readsDoubledQuotesWithoutTreatingOtherCharactersAsSql()
    {
        assertThat(Identifiers.unquote("`a``b`")).isEqualTo("a`b");
        assertThat(Identifiers.unquote("\"a\"\"b\"")).isEqualTo("a\"b");
        assertThat(Identifiers.unquote("`sales.orders`")).isEqualTo("sales.orders");
        assertThat(Identifiers.unquote("'literal'")).isEqualTo("'literal'");
        assertThat(Identifiers.unquote("`unfinished")).isEqualTo("`unfinished");
        assertThat(Identifiers.unquote("`mismatch\"")).isEqualTo("`mismatch\"");
    }

    @Test
    void keepsUntrustedColumnNamesInsideOneQuotedIdentifier()
    {
        assertThat(Identifiers.quote("card` FROM orders; DROP TABLE orders; --"))
                .isEqualTo("`card`` FROM orders; DROP TABLE orders; --`");
        assertThat(Identifiers.quote("sales.orders")).isEqualTo("`sales.orders`");
        assertThat(Identifiers.quote("select")).isEqualTo("`select`");
        for (String name : List.of("", "a`b", "`", "名 字", "line\nbreak", "a\"b", "{col}")) {
            assertThat(Identifiers.unquote(Identifiers.quote(name))).isEqualTo(name);
        }
    }
}
