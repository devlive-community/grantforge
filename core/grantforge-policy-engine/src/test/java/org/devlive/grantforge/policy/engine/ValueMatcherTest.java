// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValueMatcherTest
{
    private static final ResourceLevel PATH = ResourceLevel.of("path", null, MatcherKind.PATH, true);
    private static final ResourceLevel NAME = ResourceLevel.of("name", null, MatcherKind.WILDCARD, false);

    @Test
    void normalizesAndKnowsWhatEveryMatchStartsWith()
    {
        assertThat(ValueMatcher.normalize(PATH, "/a/b//")).isEqualTo("/a/b");
        assertThat(ValueMatcher.normalize(PATH, "/")).isEqualTo("/");
        assertThat(ValueMatcher.normalize(NAME, "Sales")).isEqualTo("sales");
        assertThat(ValueMatcher.compile(PATH, "/data/*/x", false).literalPrefix()).isEqualTo("/data/");
        assertThat(ValueMatcher.compile(NAME, "?x", false).literalPrefix()).isEmpty();
        assertThat(ValueMatcher.compile(NAME, "*", false)).isSameAs(ValueMatcher.ANY);
        assertThat(ValueMatcher.ANY.matches("anything")).isTrue();
    }

    @Test
    void wildcardsInNamesCrossEverythingButInPathsStayWithinASegment()
    {
        assertThat(ValueMatcher.compile(NAME, "a*z", false).matches("a/b/z")).isTrue();
        assertThat(ValueMatcher.compile(PATH, "/a*z", false).matches("/a/b/z")).isFalse();
        assertThat(ValueMatcher.compile(PATH, "/a?", false).matches("/ab")).isTrue();
        assertThat(ValueMatcher.compile(PATH, "/a.b", false).matches("/axb")).isFalse();
        assertThat(ValueMatcher.compile(PATH, "/a", true).matches("/a/b/c")).isTrue();
        assertThat(ValueMatcher.compile(PATH, "/a", true).matches("/ab")).isFalse();
        assertThat(ValueMatcher.compile(PATH, "/", true).matches("/x")).isTrue();
    }
}
