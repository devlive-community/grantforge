// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerTest
{
    @Test
    void sortsTheGroupsAndKeepsThemFixed()
    {
        Caller caller = new Caller("alice", List.of("sales", "analysts", "sales"), "20261019_000001_00000_abcde");
        assertThat(caller.user()).isEqualTo("alice");
        assertThat(caller.groups()).containsExactly("analysts", "sales");
        assertThat(caller.query()).isEqualTo("20261019_000001_00000_abcde");
        assertThatThrownBy(() -> caller.groups().add("admins")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copiesTheGroupsItIsGiven()
    {
        List<String> groups = new ArrayList<>(List.of("b", "a"));
        Caller caller = new Caller("bob", groups, null);
        groups.add("c");
        assertThat(caller.groups()).containsExactly("a", "b");
        assertThat(caller.query()).isNull();
        assertThat(new Caller("bob", List.of("x"), null)).isEqualTo(new Caller("bob", Set.of("x"), null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void needsAUser()
    {
        assertThatNullPointerException().isThrownBy(() -> new Caller(null, List.of(), null)).withMessage("user");
    }
}
