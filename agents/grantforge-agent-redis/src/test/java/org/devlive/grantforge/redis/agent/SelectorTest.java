// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class SelectorTest
{
    @Test
    void writesItselfAsAclSetuserTakesIt()
    {
        Selector selector = Selector.of(List.of("+@read", "-@dangerous"), new TreeSet<>(List.of("%W~b", "%R~a")), new TreeSet<>(List.of("&news:*")));
        assertThat(selector.commands()).isEqualTo("-@all +@read -@dangerous");
        assertThat(selector.rule()).isEqualTo("(%R~a %W~b &news:* -@all +@read -@dangerous)");
        assertThat(Selector.of(List.of(), new TreeSet<>(), new TreeSet<>()).rule()).isEqualTo("(-@all)");
    }

    @Test
    void readsWhatRedisShows()
    {
        Selector shown = Selector.shown(" -@all +@read ", "%W~b  %R~a", "");
        assertThat(shown).isEqualTo(Selector.of(List.of("+@read"), new TreeSet<>(List.of("%R~a", "%W~b")), new TreeSet<>()));
        assertThat(shown.channels()).isEmpty();
    }
}
