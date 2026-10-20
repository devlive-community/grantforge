// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerTest
{
    @Test
    void sortsTheGroupsOnceAndKeepsThemFixed()
    {
        List<String> groups = new ArrayList<>(List.of("sales", "analysts", "sales"));
        Caller caller = new Caller("alice", groups, "10.0.0.7");
        groups.add("admins");
        assertThat(caller.user()).isEqualTo("alice");
        assertThat(caller.groups()).containsExactly("analysts", "sales");
        assertThat(caller.clientIp()).isEqualTo("10.0.0.7");
        assertThatThrownBy(() -> caller.groups().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new Caller("bob", List.of(), null).clientIp()).isNull();
    }

    @Test
    void takesTheFirstForwardedAddressForTheClient()
    {
        assertThat(Caller.client("10.0.0.1", Arrays.asList(null, " ", " 203.0.113.9 ", "10.0.0.2"))).isEqualTo("203.0.113.9");
        assertThat(Caller.client(" 10.0.0.1 ", List.of())).isEqualTo("10.0.0.1");
        assertThat(Caller.client("10.0.0.1", null)).isEqualTo("10.0.0.1");
        assertThat(Caller.client(" ", null)).isNull();
        assertThat(Caller.client(null, null)).isNull();
    }
}
