// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextsTest
{
    @Test
    void tidiesNamesListsAndOptionalTexts()
    {
        List<String> names = Texts.of(Arrays.asList(" a ", null, "", "b", "a"));
        assertThat(names).containsExactly("a", "b");
        assertThat(Texts.of(null)).isEmpty();
        List<Integer> numbers = Texts.list(Arrays.asList(1, null, 2));
        assertThat(numbers).containsExactly(1, 2);
        assertThat(Texts.list(null)).isEmpty();
        assertThat(Texts.optional(" x ")).isEqualTo("x");
        assertThat(Texts.optional(" ")).isNull();
        assertThat(Texts.optional(null)).isNull();
    }
}
