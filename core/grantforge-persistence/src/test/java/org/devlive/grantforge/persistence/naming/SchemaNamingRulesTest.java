// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.naming;

import org.devlive.grantforge.persistence.naming.SchemaNamingRules.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaNamingRulesTest
{
    @ParameterizedTest
    @ValueSource(strings = {"gf_user_account", "gf_role_grant", "gf_x1"})
    void acceptsPortableTableNames(String name)
    {
        assertThat(SchemaNamingRules.violations(Kind.TABLE, name)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "user_name", "created_at", "tenant_id", "a1_b2"})
    void acceptsPortableColumnNames(String name)
    {
        assertThat(SchemaNamingRules.violations(Kind.COLUMN, name)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void blankNamesAreReported(String name)
    {
        assertThat(SchemaNamingRules.violations(Kind.COLUMN, name)).containsExactly("column name must not be blank");
    }

    @Test
    void reportsLengthCaseAndPrefix()
    {
        assertThat(SchemaNamingRules.violations(Kind.COLUMN, "a_really_long_column_name_over_limit"))
                .singleElement().asString().contains("longer than 30");
        assertThat(SchemaNamingRules.violations(Kind.COLUMN, "createdAt")).singleElement().asString().contains("snake_case");
        assertThat(SchemaNamingRules.violations(Kind.TABLE, "account")).singleElement().asString().contains("'gf_'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_id", "1st", "double__underscore", "trailing_", "with-dash", "été", "Gf_upper"})
    void rejectsNonSnakeCase(String name)
    {
        assertThat(SchemaNamingRules.violations(Kind.COLUMN, name)).anySatisfy(v -> assertThat(v).contains("snake_case"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "level", "order", "comment", "size", "number", "key", "session", "rownum"})
    void rejectsWordsReservedOnAnySupportedDatabase(String name)
    {
        assertThat(SchemaNamingRules.violations(Kind.COLUMN, name)).anySatisfy(v -> assertThat(v).contains("reserved"));
    }

    @Test
    void reservedWordsAreStoredLowerCase()
    {
        assertThat(SchemaNamingRules.RESERVED_WORDS).allSatisfy(word -> assertThat(word).isLowerCase());
    }
}
