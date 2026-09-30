// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StringsTest
{
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n", " "})
    void blankToNullTreatsNullAndBlankAsAbsent(String value)
    {
        assertThat(Strings.blankToNull(value)).isNull();
    }

    @Test
    void blankToNullStripsSurroundingWhitespace()
    {
        assertThat(Strings.blankToNull("  admin \t")).isEqualTo("admin");
        assertThat(Strings.blankToNull("a b")).isEqualTo("a b");
    }

    @Test
    void requireNonBlankReturnsStrippedValue()
    {
        assertThat(Strings.requireNonBlank(" code ", "code")).isEqualTo("code");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void requireNonBlankRejectsNullAndBlank(String value)
    {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Strings.requireNonBlank(value, "username"))
                .withMessage("username must not be blank");
    }
}
