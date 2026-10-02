// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChecksTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesAndRefusesMissingOrBlankValues()
    {
        assertThatThrownBy(() -> Checks.notNull(null, "x")).isInstanceOf(NullPointerException.class).hasMessage("x is required");
        assertThatThrownBy(() -> Checks.text(" ", "x")).isInstanceOf(IllegalArgumentException.class);
        List<String> source = new ArrayList<>(List.of("a"));
        List<String> copy = Checks.list(source, "x");
        source.add("b");
        assertThat(copy).containsExactly("a");
        assertThatThrownBy(() -> Checks.list(Arrays.asList("a", null), "x")).isInstanceOf(NullPointerException.class);
        assertThat(Checks.texts(List.of("a", "a", "b"), "x")).containsExactly("a", "b");
        Map<String, String> map = new HashMap<>();
        map.put(" ", "v");
        assertThatThrownBy(() -> Checks.map(map, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Checks.map(Map.of("k", "v"), "x")).containsEntry("k", "v");
    }
}
